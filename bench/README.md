# Hashbench - hướng dẫn benchmark

Hashbench là bộ đo Java 8 cho RQ1, RQ2 và RQ3. Mã nguồn Java nằm trong
`hashbench/src`; `build.bat` tạo `hashbench/out/hashbench.jar`. Có thể dùng
NetBeans 17 với JDK 1.8 hoặc Ant (`ant clean jar`).

## Thuật toán và phạm vi đo

Năm thuật toán/biến thể triển khai được dùng trong sáu cấu hình phân tích:

- `sc/base`: Separate Chaining.
- `lp-tomb/base`, `tau=inf`: Linear Probing với tombstone, không rebuild.
- `lp-tomb/base`, `tau` hữu hạn: Linear Probing với tombstone và rebuild.
- `lp-backshift/base`: Linear Probing với dịch lùi theo Knuth Algorithm R.
- `rh/base`: Robin Hood với early-stop.
- `rh/noearly`: Robin Hood không early-stop; CSV lưu `algorithm=rh`,
  `variant=noearly`.

Các pha đã chạy chính thức:

- RQ1: `build`, `present`, `absent`.
- RQ2: `build`, `workload`, `present`, `absent`, `ctl_present`, `ctl_absent`.
- RQ3: `build`, `workload`, `present`, `absent`.

Dữ liệu đã niêm phong nằm trong `results/raw/official/`: 200 tiến trình RQ1 và
360 tiến trình RQ2/RQ3. Các tiến trình timeout và vi phạm variance được giữ
nguyên, không sửa CSV gốc để ép validator PASS.

## Build và test

Ở thư mục gốc dự án, chọn một trong các cách build:

```powershell
.\build.bat
```

```powershell
ant clean jar
```

Chạy toàn bộ test:

```powershell
java -Xmx2g -cp hashbench\out hashbench.test.AllTests
```

Smoke test runtime/JAR:

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --selftest
```

## Các chế độ và cờ CLI

| Cờ/chế độ | Tác dụng |
|---|---|
| `--run` | Chạy runner stub để kiểm tra cách ly tiến trình; hỗ trợ `--timeout-sec N` và `--machine-id ID`. Đây không phải phép đo RQ chính thức. |
| `--count` | Chế độ stub có bật bộ đếm; với benchmark thật, thêm `--count` vào lệnh `--bench`. Với batch, cờ đếm được đặt trên từng dòng cấu hình bằng `count=1`; `--batch` không có cờ `--count` riêng. |
| `--official` | Tùy chọn của `--batch`; bật các điều kiện bảo vệ cho lần chạy chính thức, trong đó `hashbench/` và `bench/` phải sạch. |
| `--rerun <run_id> --csv <file>` | Đọc cấu hình và dòng đích trong CSV, chạy lại tiến trình, rồi so status, output_check và thời gian với median cấu hình tham chiếu. Tạo CSV tạm dưới `results/raw/dev/`. |
| `--selftest` | In phiên bản Java và kiểm tra put/get/remove cơ bản trên `StubHashIndex`. Không thay thế toàn bộ test. |
| `--limit N` | Tùy chọn của `--batch`; dừng sau khi xử lý N cấu hình batch (các cấu hình đã resume/skip cũng được tính là đã xử lý). Dùng cho pilot, không dùng để tuyên bố đã chạy đủ ma trận. |

Lệnh batch đầy đủ có dạng:

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --batch bench\batch_rq1.txt --out results\raw\official\rq1_results.csv --log results\raw\batch_log.txt --machine-id <id> --xmx 2g --timeout-sec 60 --official
```

Sinh ma trận RQ1/RQ2-RQ3 và chạy batch:

```powershell
java -jar hashbench\out\hashbench.jar --batch-gen-rq1 --out bench\batch_rq1.txt
java -jar hashbench\out\hashbench.jar --batch-gen-rq23 --out bench\batch_rq23.txt
```

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --batch bench\batch_rq23.txt --out results\raw\official\rq23_results.csv --log results\raw\batch_log.txt --machine-id <id> --xmx 2g --timeout-sec 60 --official
```

## Rerun mẫu từ dữ liệu chính thức

Chạy ở thư mục gốc repo; mỗi lệnh dưới đây nhắm một pha của một RQ. Timeout
mặc định là 60 giây cho mỗi pha tiến trình con.

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --rerun RQ1_sc_base_rq1a_p20_a0p5_tinf_s1_r1_present --csv results\raw\official\rq1_results.csv
```

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --rerun RQ2_lp-tomb_base_rq2_p20_a0p0_tinf_l0p5_c0_s1_r1_present --csv results\raw\official\rq23_results.csv
```

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --rerun RQ3_lp-tomb_base_rq3_p20_a0p0_t0p2_l0p5_c1048576_s1_r1_present --csv results\raw\official\rq23_results.csv
```

`--rerun` không thay đổi CSV nguồn. Nó tạo một file rerun dưới `results/raw/dev/`;
hãy giữ hoặc dọn file đó theo quy trình lưu bằng chứng của nhóm.

## Probe, tau và các cột thống kê

Chỉ lượt `--count` ghi tổng probe vào `ops_count`. SC đếm node đã duyệt; các
thuật toán dò mở đếm ô đã xem theo implementation. Do đơn vị probe không giống
nhau giữa chaining và open addressing, không diễn giải các số này như cùng một
đại lượng vật lý mà không ghi chú quy ước. Số probe trung bình mỗi truy vấn là
`ops_count / queries`.

Với `lp-tomb`, rebuild kích hoạt khi `tomb_count >= tau * M` (`M` là capacity).
`tau=inf` tắt rebuild; tau hữu hạn điều khiển ngưỡng rebuild và được ghi trong
cột `params`. Các thuật toán khác bỏ qua tau.

Tám cột thống kê mở rộng:

| Cột | Ý nghĩa |
|---|---|
| `tomb_ratio` | Tỷ lệ tombstone trên capacity. |
| `empty_ratio` | Tỷ lệ ô rỗng trên capacity. |
| `rebuild_count` | Số lần rebuild. |
| `rebuild_ms` | Tổng thời gian rebuild, ms. |
| `block_median_ms` | Median thời gian khối workload, ms. |
| `block_max_ms` | Thời gian khối lớn nhất, ms. |
| `dib_mean` | DIB trung bình, nếu thuật toán hỗ trợ. |
| `dib_max` | DIB lớn nhất, nếu thuật toán hỗ trợ. |

`machine_id` để trống trong hai CSV chính thức hiện tại. Cấu hình đo được mô tả
trong `results/raw/machine.md`: Intel Core i7-10850H, 31,8 GB RAM, Windows 11
Pro, Temurin Java 8u504. File này là metadata cấp bộ dữ liệu, không phải giá trị
được join bằng `machine_id` ở từng dòng.

## Tóm tắt và validator

Tạo `summary.csv` và sáu bộ biểu đồ PNG/PDF:

```powershell
python bench\analyze.py
```

Validator đọc CSV, không sửa nội dung:

```powershell
java -cp hashbench\out hashbench.CsvValidator --latest results\raw\official\rq1_results.csv results\raw\official\rq23_results.csv
```

Ở bộ dữ liệu hiện tại, RQ1 PASS; RQ2/RQ3 có một vi phạm variance đã được ghi
trong `results/raw/rerun_log.md` và README gốc. Đây là ngoại lệ thời gian của
một pha `ctl_present`; không xóa dòng hay làm thay đổi CSV chính thức để che
vi phạm.
