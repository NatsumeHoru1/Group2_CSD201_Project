# TV4 — Test harness, Oracle, Workload, Bảng đối chứng, Giữ repo/tag

**Cặp P2 · Nhóm 2 · CSD201 FA26 · Đề tài 2 (Hash Table, Robin Hood)**
Căn cứ: Kế hoạch bản 2.2 (mục 1.5, 2, 3, 4, 5) và bảng "Chia theo cặp".

## 1. Phạm vi và ranh giới

| TV4 sở hữu | Không phải của TV4 (chỉ phối hợp) |
|---|---|
| Test harness tự chứa (không JUnit), `Registry`, 6 cấu hình kiểm thử | `lp-tomb`, rebuild, thống kê TOMB/EMPTY, bộ đo RQ2 → **TV3** |
| Oracle 10^6 thao tác (put/get/remove = 40/40/20) | `Hashing`, `LongSet`, generator, SC, runner, CSV writer → **TV1/TV2** |
| Workload xóa–thêm (pool khóa, dãy khóa xóa, khối 10^4) | `rh`, `rh-noearly` → **TV5**; `lp-backshift`, validator CSV → **TV6** |
| Bảng đối chứng (dựng lại từ đúng tập khóa sống) | |
| Vai chung **Giữ repo/tag**: nhánh, PR, tag, kiểm cây sạch, đóng băng, gói nộp | |

Hạn cứng của TV4: **harness + oracle lên `main` cuối D3** (cả nhóm phụ thuộc vào đây).

## 2. Gói bàn giao (đã viết và chạy thử)

```
src/hashbench/
  HashIndex.java, HashStats.java, Validatable.java   (bản chung; khi merge dùng bản trên main)
  test/  TestHarness, TestUtil, Registry, CoreCases, OracleTest, WorkloadTests, AllTests
  test/ref/  ReferenceIndex (HashMap), BuggyIndex (4 bản đột biến)
  workload/  Workload, ControlTable, QueryBench
scripts/run_tests.bat, scripts/check_clean.bat
.github/pull_request_template.md, .gitignore, .gitattributes
```

Lệnh chạy (Windows, JDK 1.8): `scripts\run_tests.bat` · `--self` tự kiểm tra harness · `--ops N` đổi số thao tác oracle · `--only lp-tomb` chạy một cấu hình.
Exit code: 0 xanh, 1 có FAIL, 2 chưa đăng ký cài đặt nào.

**Cách mỗi cặp nối vào:** bỏ comment đúng dòng của mình trong `Registry.all()` (tên lớp và constructor trong file là *giả định*, sửa theo code thật). Cài đặt nên implement thêm `Validatable` (bất biến) và `HashStats` (để harness kiểm TOMB/EMPTY/rebuild).

## 3. Mức đã kiểm chứng (nói thẳng)

- Đã biên dịch được với `-source 8 -target 8` và chạy `--self`: bản tham chiếu **12/12 PASS**, **4/4 bản đột biến bị bắt** (nhận `MIN_VALUE`, không giới hạn FULL, sai mã UPDATED, remove làm mất khóa lân cận).
- **Chưa chạy trên 5 thuật toán thật** (chưa có code của các cặp). Lần đầu tích hợp có thể lòi lỗi; đó chính là mục đích của harness.
- Máy mình chỉ có JDK 21 nên `-source 8` không chặn được API Java 9+. Phải chạy `scripts\run_tests.bat` bằng **JDK 1.8 thật** trước khi tag `rc1`.

## 4. Đặc tả kỹ thuật

### 4.1 Harness
`test(tên, Body)` bắt mọi `Throwable`, đếm PASS/FAIL, in tổng kết. `sandbox()` chạy mà không tính điểm, dùng cho kiểm tra đột biến. `withTimeout()` chạy trong luồng riêng để bắt vòng lặp vô hạn (lỗi quên giới hạn dò M ô, rủi ro B11).

### 4.2 Bộ ca trên bảng M = 16 (`CoreCases`)
1. cơ bản put/get/update/remove, thêm lại sau remove
2. `put(MIN_VALUE)` phải ném `IllegalArgumentException` và không đổi bảng (B14)
3. bảng đầy: `FULL`, `size` không đổi, dữ liệu cũ nguyên vẹn, update vẫn được, xóa 1 rồi chèn lại được (B10: Robin Hood không được hoán đổi trước khi biết đầy)
4. bảng đầy: `get`/`remove` khóa vắng phải dừng (giới hạn dò M ô)
5. **cụm quấn vòng ô 15→0..3**, xóa từng phần tử, 2 thứ tự chèn (ca bổ sung "xóa giữa cụm quấn vòng" của bản 2.1)
6. xóa–thêm 20 000 bước ở tải 8/16 và 14/16 đối chiếu `HashMap`, gọi `validate()` mỗi bước
7. thống kê: lp-tomb có `EMPTY + TOMB + size == M`; cài đặt không tombstone có `TOMB == 0`; có rebuild thì `rebuildCount > 0`, không rebuild thì `== 0`

Khóa có home cho trước dùng bản **băm độc lập** trong `TestUtil` (không gọi `Hashing` của TV2) để test có giá trị đối chứng.

### 4.3 Ánh xạ "6 ca bắt buộc của đề" — CẦN LÀM KHI CÓ ĐỀ
Kế hoạch chỉ cho biết: **ca 4** là bảng dò mở, **ca 5** là cụm quấn vòng, **ca 6** là lp-tomb có rebuild; ca 1–3 mình không có nội dung. Vì vậy mình **không bịa** ca 1–3. Việc còn lại: mở đề, đặt tên 6 test theo đúng thứ tự đề (`caBatBuoc1…6`), đối chiếu với bảng 7 ca ở trên, bổ sung ca còn thiếu. Ca 6 cần `HashStats`; cấu hình rebuild trong `Registry` dùng `TAU = 0.20`.

### 4.4 Oracle (`OracleTest`)
10^6 thao tác, M = 2^12, ba miền khóa 0,5M / M / 2M (B9). Với mỗi `put`, giá trị mong đợi là `UPDATED` nếu khóa đã có, `FULL` nếu `size == M` và khóa chưa có, ngược lại `INSERTED`; oracle chỉ cập nhật khi bảng không trả `FULL`. So `size` sau từng thao tác, quét toàn miền mỗi 10^5 thao tác, gọi `validate()` mỗi 16 384 thao tác. Có chốt chặn: miền 2M mà không bao giờ gặp `FULL` thì test **fail** (oracle không phủ nhánh đầy). Giá trị không bao giờ là `MIN_VALUE`.

### 4.5 Workload (`Workload`)
- Sinh trước ngoài vùng đo bằng `SplittableRandom(seed)`: `n` khóa ban đầu + `C` khóa chèn, phân biệt (tập long primitive, ô rỗng `-1L`; thay bằng `LongSet` của TV2 khi có).
- Bước `i`: `remove(victims[i])` rồi `put(insertKeys[i])`. `victims[i]` luôn là khóa đang sống, nên `remove` bắt buộc trả `true` (`badRemoves` phải bằng 0).
- `execute(table, opsPerBlock, listener)`: `nanoTime` chỉ bao từng khối; `listener.onBlock(...)` chạy **ngoài** vùng đo → TV1 dùng để ghi file tạm và flush ngay sau mỗi khối (cơ chế thu hoạch khi timeout, B2).
- `Result` có `totalNanos`, `blockNanos[]`, `medianBlockNanos()`, `maxBlockNanos()`, `fullCount`, `badRemoves`.
- Truy vấn có mặt: lấy mẫu có hoàn lại từ tập khóa sống cuối (B6). Truy vấn vắng mặt: khóa **chưa từng** nằm trong pool.
- `verify(table)` kiểm `size == n` và mọi khóa sống đúng giá trị (ngoài vùng đo; kết quả dùng cho `output_check`).

### 4.6 Bảng đối chứng (`ControlTable`)
Dựng mới cùng thuật toán từ đúng `finalLive` (cùng giá trị), rồi chạy đúng bộ 10^6 truy vấn qua `QueryBench.timeGets`. `WorkloadTests` kiểm `checksum` của bảng đã lão hóa và bảng đối chứng **bằng nhau** (cả có mặt lẫn vắng mặt).

## 5. Vai "Giữ repo/tag"

**Nhánh:** `main` luôn build và xanh; mỗi cặp một nhánh (`p1-…`, `p2-…`, `p3-…`); merge bằng PR, review chéo P1→P2→P3→P1; không push thẳng `main`. Nếu giảng viên có quy định Git riêng, quy định đó thắng.

**Tag:**

| Tag | Khi nào | Điều kiện trước khi gắn |
|---|---|---|
| `d2-plan` | cuối D2 | PDF kế hoạch 1 trang đã vào `paper/`; sau đó giả thuyết đóng băng |
| `rc1` | cuối D7 | test xanh cả 6 cấu hình bằng JDK 1.8; runner ra 1 CSV hợp lệ; cây sạch |
| `measure-v1` | sau pilot sáng D8 | pilot chạy hết ma trận; chỉ sửa bug từ đây |
| `submission` | D13 | clone sạch build/test/rerun được; hình tạo lại từ script; `git_commit` trong CSV khớp |

**Trước mỗi tag:** `scripts\check_clean.bat` (`git status --porcelain -- hashbench bench` phải rỗng) → `scripts\run_tests.bat` → ghi hash commit vào `PHAN_CONG.md`. Nếu sửa bug sau `measure-v1`: tag mới (`measure-v2`), giữ CSV cũ, chỉ dùng dòng của commit được chốt.

**Phiên bản:** `results/raw/` commit ngay sau mỗi batch, không sửa tay. Mời giảng viên làm collaborator, kiểm tra họ vào được repo trước hạn nộp.

## 6. Lịch theo ngày

| Ngày | Việc TV4 | Đầu ra / kiểm |
|---|---|---|
| Trước D0 | Smoke test đường dẫn có dấu "dự án"; dựng nhánh, `.gitignore`, `.gitattributes`, PR template | repo khung |
| D0 | Kickoff; xác nhận A/B; xin đề để lập bảng 6 ca bắt buộc | câu hỏi B1, B2, B7, B14 đã gửi |
| D1–D2 | Chạy `--self` trên JDK 1.8; chốt quy ước đơn vị "khối" và tỷ lệ 40/40/20 với TV3 | `CONVENTIONS` mục workload/test |
| D2 | Tag `d2-plan`; chốt danh sách seed | tag |
| **D3** | **Merge harness + oracle + `Registry` lên `main`** | cả nhóm gắn test của mình |
| D3–D4 | Review PR của P3 (vì P2 review P3); hoàn thiện 6 ca bắt buộc theo đề | test xanh trên `lp-tomb` |
| D4–D5 | `Workload`, `ControlTable` nối với runner của TV1 (listener ghi file tạm); hỗ trợ TV3 phần workload RQ3 | khối 10^4, flush từng khối |
| D6 | Rà toàn bộ 6 cấu hình × oracle; dượt D7 chiều | báo cáo test |
| D7 | Cổng 10 phút; sửa bug; JDK 1.8 thật; tag `rc1` | `rc1` |
| D8 | Pilot cùng chủ máy đo; tag `measure-v1` | `measure-v1` |
| D8–D10 | Commit `results/raw/` sau mỗi batch; D10 dự phòng | CSV đủ |
| D11 | Dượt chạy lại ≥ 10 dòng (hỗ trợ chủ máy đo); viết mục "kiểm tra đúng" cho Methods | ghi dòng nhiễu |
| D12 | Script phân tích + hình RQ2 (hoặc lời, đổi vai với TV3) | hình tạo lại bằng 1 lệnh |
| D13 | Clone sạch, build, test, rerun mỗi RQ < 60 s; kiểm `git_commit`; tag `submission` | gói nộp |
| D13–D14 | Nộp sớm, xác nhận giảng viên truy cập; dượt cá nhân; bảo vệ | |

## 7. Điểm cần xác nhận (mặc định đã dùng trong code)

1. **"Khối 10^4 thao tác"** = 10^4 lệnh `remove`/`put` riêng lẻ (tức 5 000 cặp), nên `execute` đếm theo lệnh. Nếu đề hiểu là 10^4 cặp, đổi `opsPerBlock` thành 20 000. Hỏi giảng viên cùng B7.
2. **"40/40/20"** = put/get/remove. Nếu đề ghi thứ tự khác, đổi hai ngưỡng `40` và `80` trong `OracleTest`.
3. Kiểu trả về `HashStats`, tên lớp và constructor trong `Registry` là giả định theo mô tả trong kế hoạch.
4. `lp-tomb+rebuild` trong test dùng `t = 0,2`.

## 8. Chuẩn bị kiểm tra cá nhân (5 phút, không AI, 40% điểm)

Phần đứng tên: **harness, oracle, workload, bảng đối chứng, quản trị repo/tag**. Phải tự giải thích được, không cần xem code:

- Vì sao `put` khi đầy phải kiểm `size == M` **trước** khi hoán đổi (Robin Hood)? Vì sao oracle chỉ cập nhật khi không `FULL`?
- Vì sao test khóa có home cho trước dùng băm độc lập? Vì sao `remove` trong workload luôn trả `true`?
- Vì sao `nanoTime` chỉ bao từng khối, listener ở ngoài? Vì sao truy vấn vắng mặt dùng khóa chưa từng vào pool?
- Vì sao bảng đối chứng cho `checksum` bằng bảng đã lão hóa (cùng tập khóa, cùng giá trị)?

**Trace tay 5 thuật toán trên M = 8/16**, bắt buộc luyện ca cụm quấn vòng (phần tử ở ô 15, 0, 1, 2; xóa phần tử ô 0 rồi tìm phần tử sau nó): LP-tomb đặt TOMB; lp-backshift dịch lùi (Algorithm R); rh dịch lùi và giảm DIB; sc xóa node khỏi danh sách.

**Sửa nhỏ + test** (luyện): đổi `TAU`; đổi tỷ lệ 40/40/20; đổi kích thước khối; thêm một ca test mới vào `CoreCases`; thêm một bản đột biến vào `BuggyIndex` rồi xem harness bắt.

**Dự đoán cấu hình chưa chạy:** lp-tomb không rebuild, tải 0,5, C = 4M → truy vấn vắng mặt dò gần đủ M ô, có thể timeout; rebuild bật thì thời gian khối có đột biến định kỳ.

## 9. Mẫu `PHAN_CONG.md` cho TV4 (không ghi %, ghi theo việc/commit/PR, cập nhật hằng ngày)

```
### TV4
- D3: Harness + Registry + OracleTest -> PR #<số>, review bởi P3
- D4: ...
```

## 10. Rủi ro của TV4

| Rủi ro | Phòng ngừa |
|---|---|
| Harness sai khiến cả nhóm tin nhầm | `--self` với 4 bản đột biến; giữ đối chứng độc lập |
| Oracle chậm với lp-tomb không rebuild ở miền 2M | chạy `--only` từng cấu hình; hạ `--ops` khi dev, đủ 10^6 khi chốt |
| Mã băm độc lập lệch bản của TV2 | test hằng số của TV2 so với `TestUtil.FIB` |
| Quên JDK 1.8 thật | `run_tests.bat` bắt buộc dùng `JAVA8_HOME` |
| Tag gắn khi cây bẩn | `check_clean.bat` trước mỗi tag |
