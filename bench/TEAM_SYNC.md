# TEAM\_SYNC - cập nhật 25/10 (TV1)

Commit: <điền hash sau khi push> f901972. Mọi người `git pull --rebase` trước khi làm tiếp.

## Đã có trên main

* Runner đo thật cho RQ1 (thuật toán rh). Lệnh mẫu (PowerShell, chạy ở thư mục gốc dự án):
`.\\build.bat`
`java -Xmx2g -jar hashbench\\out\\hashbench.jar --bench --rq RQ1 --algo rh --variant base --dataset rq1a --p 18 --alpha 0.75 --seed 1 --queries 1000000`
* CSV 32 cột (20 cột bắt buộc + 12 cột riêng), xem `CsvSchema.java`. Tiến trình con ghi log từng pha nên khi timeout vẫn giữ được các pha đã xong.
* Test: RunnerPhaseTest, MeasureChildTest, TV2SelfTest. Cả ba phải xanh trước khi push.
`java -cp hashbench\\out hashbench.RunnerPhaseTest` (tương tự cho hai test còn lại)

## Cách cắm thuật toán vào runner (TV3, TV4, TV6)

Thêm nhánh `if` trong `HashIndexFactory.create(...)`. Thuật toán phải implements `HashIndex` (và `HashStats` nếu có thống kê), và gọi `Counters.addProbes(...)` trong get để lượt --count đếm được.
Tên thuật toán: `sc`, `lp-tomb`, `lp-backshift`, `rh` (variant `noearly` cho rh-noearly, KHÔNG đặt algorithm="rh-noearly").
Chữ ký factory hiện chưa có `tau`. TV3 đề xuất đổi thành `create(algo, variant, p, tau)`. Trả lời trên nhóm trước khi sửa để khỏi vỡ build của người khác.

## Việc từng người

* TV2: (1) `generateClusteredKeys` sai đặc tả: đề yêu cầu 10% khóa có h(k) < M/64, 90% còn lại phân bố đều; hiện 100% khóa nằm trong cụm. (2) `computeSHA256` dùng String.format từng byte, đổi sang bảng hex cho nhanh. (3) `verify\_env.bat`. (4) Chốt danh sách 3 seed và commit vào repo.
* TV3: `lp-tomb` (EMPTY/TOMB, put nhớ TOMB đầu tiên, dò tối đa M ô), rebuild theo tau. Đẩy lên main sớm nhất có thể và báo ngày giao.
* TV4: test harness + oracle 10^6 thao tác + 6 ca bắt buộc, báo trạng thái. Workload RQ2/RQ3 phụ thuộc lp-tomb.
* TV5: rh đã dùng được. Chạy `validate()` sau oracle 10^6 thao tác và báo kết quả. Hỗ trợ lp-backshift nếu TV6 chậm.
* TV6: `lp-backshift` (ghi rõ Algorithm R hay chèn lại cụm vào params), validator CSV theo 32 cột trong `CsvSchema.java`.
  Đã xong (25/10): `LinearProbingBackshiftTable` (dò mở tuyến tính + dịch lùi cụm theo
  Algorithm R của Knuth, không tombstone), đã cắm vào `HashIndexFactory` với
  `algo=lp-backshift, variant=base`. Cột params ghi thêm `backshift=knuth-r` (do
  `CsvParams.augment()` nối thêm, không sửa cặp có sẵn).
  Validator CSV: `CsvValidator.java`, chạy
  `java -cp hashbench\out hashbench.CsvValidator <file.csv> [...]` (exit 0 = PASS,
  1 = có vi phạm). Test: `LpBackshiftSelfTest` (oracle 10^6 thao tác trên 6 cấu
  hình, 6 ca bắt buộc + ca xóa giữa cụm quấn vòng, bộ đếm ô đã xem, validator).
  Lệnh đo RQ1 cho lp-backshift xem `bench/README.md`.

## Quy ước đang áp dụng (bản nháp bench/CONVENTIONS.md; ai thấy sai báo ngay)

* 1 dòng CSV = 1 pha; run\_id = proc\_id + "\_" + phase. RQ1: pha build, present, absent.
* n = round(alpha \* M); input\_sha256 = SHA-256 của khóa có mặt rồi tới dãy truy vấn vắng mặt, big-endian.
* output\_check = tổng long các giá trị get tìm thấy + số get không tìm thấy (tất định theo seed).
* Truy vấn có mặt lấy mẫu có hoàn lại từ tập khóa.
* ops\_count chỉ ghi ở lượt --count. Pha không chạy: status timeout/crash/oom, elapsed\_ms trống, phase\_note=not\_started.

## Mốc sắp tới

* Hôm nay/ngày mai: mỗi thuật toán lên main kèm test của mình xanh. Ai chưa kịp thì báo TV1 ngay để rút gọn đo.
* TV1: resume, git\_commit, --rerun, rồi pilot toàn ma trận ngay khi đủ thuật toán.
* Nếu thiếu thuật toán thì đo RQ1 với những cái đã có trước; không cắt seed hay lượt lặp.

