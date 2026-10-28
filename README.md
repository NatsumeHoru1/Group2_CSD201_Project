# Group2_CSD201_Project

Đồ án CSD201 - Đề tài 2: Hash Table và Robin Hood Hashing.

## Môi trường và build

- Java 8 (JDK 1.8; bộ dữ liệu đo chính thức được chạy bằng Temurin 8u504).
- NetBeans 17, cấu hình Java Platform và Source/Binary Format là 1.8.
- Có thể build bằng Ant hoặc script Windows của dự án:

```powershell
ant clean jar
```

```powershell
.\build.bat
```

JAR chạy được ở `hashbench\out\hashbench.jar` khi dùng `build.bat`; build Ant/NetBeans đặt JAR trong `dist`. Dùng đúng đường dẫn JAR được tạo bởi cách build đã chọn.

## Kiểm thử

Chạy toàn bộ bộ kiểm thử:

```powershell
java -Xmx2g -cp hashbench\out hashbench.test.AllTests
```

Tự kiểm tra Java runtime và thao tác put/get/remove cơ bản:

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --selftest
```

`--selftest` chỉ là smoke test nhanh, không thay thế `hashbench.test.AllTests`.

## Benchmark và dữ liệu đã niêm phong

Hệ thống hiện có năm thuật toán/biến thể triển khai và sáu cấu hình phân tích:

1. Separate Chaining (`sc`).
2. Linear Probing với Tombstone, không rebuild (`lp-tomb`, `tau=inf`).
3. Linear Probing với Tombstone và rebuild (`lp-tomb`, `tau` hữu hạn).
4. Linear Probing với backward-shift deletion theo Knuth Algorithm R (`lp-backshift`).
5. Robin Hood (`rh/base`).
6. Robin Hood không early-stop (`rh/noearly`, lưu CSV là `algorithm=rh`, `variant=noearly`).

Các pha đo đã chạy trong dữ liệu chính thức:

- RQ1: `build`, `present`, `absent`.
- RQ2: `build`, `workload`, `present`, `absent`, `ctl_present`, `ctl_absent`.
- RQ3: `build`, `workload`, `present`, `absent`.

CSV thô được giữ tại [results/raw/official/](results/raw/official/): RQ1 có 200 tiến trình (600 dòng pha); RQ2/RQ3 có 360 tiến trình (1.584 dòng pha). Các timeout được giữ nguyên trong CSV và được phân biệt với lỗi `crash`, `oom` hay `wrong`. Không chỉnh sửa dữ liệu thô để làm cho validator đạt.

Tạo bảng tổng hợp và hình từ thư mục gốc repo:

```powershell
python bench\analyze.py
```

Kết quả được ghi vào [results/tables/summary.csv](results/tables/summary.csv) và [results/figures/](results/figures/). Chỉ dòng `status=ok` được dùng tính thống kê thời gian; timeout được giữ thành số đếm và được đánh dấu riêng khi không có kết quả thành công.

### Rerun một pha từ CSV chính thức

Chạy các lệnh sau từ thư mục gốc repo. Mỗi lệnh đọc trực tiếp CSV chính thức tương ứng và đối chiếu `status`, `output_check` cùng thời gian chạy với median các lượt cùng cấu hình. Timeout cho tiến trình con mặc định là 60 giây mỗi pha; thời gian thực tế còn phụ thuộc máy và tải hệ thống.

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --rerun RQ1_sc_base_rq1a_p20_a0p5_tinf_s1_r1_present --csv results\raw\official\rq1_results.csv
```

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --rerun RQ2_lp-tomb_base_rq2_p20_a0p0_tinf_l0p5_c0_s1_r1_present --csv results\raw\official\rq23_results.csv
```

```powershell
java -Xmx2g -jar hashbench\out\hashbench.jar --rerun RQ3_lp-tomb_base_rq3_p20_a0p0_t0p2_l0p5_c1048576_s1_r1_present --csv results\raw\official\rq23_results.csv
```

### CSV, probe và rebuild

CSV có 32 cột: 20 cột chuẩn và 12 cột mở rộng. Tám chỉ số mở rộng là:

| Cột | Ý nghĩa |
|---|---|
| `tomb_ratio` | Tỷ lệ ô tombstone trên sức chứa bảng sau workload. |
| `empty_ratio` | Tỷ lệ ô rỗng trên sức chứa bảng sau workload. |
| `rebuild_count` | Số lần rebuild trong workload. |
| `rebuild_ms` | Tổng thời gian rebuild tính bằng mili giây. |
| `block_median_ms` | Median thời gian khối workload. |
| `block_max_ms` | Thời gian lớn nhất của một khối workload. |
| `dib_mean` | DIB trung bình, khi thuật toán cung cấp thống kê. |
| `dib_max` | DIB lớn nhất, khi thuật toán cung cấp thống kê. |

`ops_count` chỉ được ghi ở lượt `--count`. Probe của SC đếm số node đã duyệt trong chain; các bảng dò mở đếm số ô đã xem theo bộ đếm của cài đặt (kể cả ô kết thúc nếu cài đặt tính ô đó). Vì đơn vị thao tác khác nhau giữa các họ thuật toán, cần nêu rõ quy ước khi diễn giải hoặc so sánh. `probes/query` được tính bằng `ops_count / queries`.

Tham số `tau` chỉ áp dụng cho `lp-tomb`: rebuild được kích hoạt khi số tombstone đạt hoặc vượt `tau * capacity`. `tau=inf` tắt rebuild; các giá trị hữu hạn trong bộ đo chính thức được lưu trong `params` của CSV.

### Nguồn gốc dữ liệu và bất thường validator

Metadata máy đo được ghi tại [results/raw/machine.md](results/raw/machine.md): Intel Core i7-10850H, RAM 31,8 GB (xấp xỉ 32 GB), Windows 11 Pro và Temurin Java 8u504. Cột `machine_id` trong CSV chính thức đang để trống; do đó metadata phần cứng được liên kết ở cấp bộ dữ liệu qua file ghi chú này, không phải qua một giá trị `machine_id` trong từng dòng CSV.

Validator hiện báo một ngoại lệ variance duy nhất trong `rq23_results.csv`: pha `ctl_present` của `RQ2_lp-tomb_base_rq2_p20_a0p0_tinf_l0p5_c0_s1_r1` có thời gian 314,931 ms, bằng 2,065 lần median 152,481 ms, vượt ngưỡng 2 lần. Dòng có `status=ok`; đây là ngoại lệ thời gian, có khả năng do biến động JIT/cold-start ở lượt đầu của cấu hình, không phải bằng chứng về sai logic. Rerun ngày 2026-10-28 cho status/output_check khớp, thời gian 157,419 ms (1,043x median 150,893 ms) và kết luận KHỚP. Median rerun khác median validator do cách nhóm mẫu khác nhau; rerun mới không thay thế dòng đo đã niêm phong. Vi phạm ngưỡng variance của validator vẫn được giữ nguyên, không được loại khỏi dữ liệu thô. Ghi chú đầy đủ ở [results/raw/rerun_log.md](results/raw/rerun_log.md).

## Tài liệu liên quan

- [bench/README.md](bench/README.md): hướng dẫn CLI, batch và phân tích.
- [bench/CONVENTIONS.md](bench/CONVENTIONS.md): schema CSV và quy ước đo.
- [data/README.md](data/README.md): generator, seeds và checksum.
- [PHAN_CONG.md](PHAN_CONG.md): phân công và phần việc đã thực hiện.
