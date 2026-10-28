# Phân công và kết quả thực hiện

Tên thành viên được ghi theo mã TV trong tài liệu đồng bộ nhóm; chỉ TV1 có tên
đầy đủ trong hồ sơ repo hiện có. Các mục dưới đây mô tả kết quả kỹ thuật trong
mã nguồn, test và dữ liệu, không suy đoán tên cá nhân chưa được cung cấp.

## TV1 - Huỳnh Anh Tuấn: Runner, workload, batch và phân tích

- Hoàn thiện Runner chạy tiến trình con cô lập, timeout, thu hoạch kết quả theo
  pha, ghi CSV và hỗ trợ tiếp tục batch.
- Tối ưu workload xóa-thêm bằng cấu trúc chỉ số sống để cập nhật phần tử trong
  thời gian O(1); kết nối đo RQ2/RQ3 với workload, bảng đối chứng và các pha
  truy vấn.
- Tích hợp CLI cho batch, `--official`, `--limit`, `--rerun`, `--selftest` và
  các chế độ runner; phối hợp schema và validator CSV.
- Thực thi ma trận chính thức gồm 200 tiến trình RQ1 và 360 tiến trình RQ2/RQ3.
  CSV được niêm phong trong `results/raw/official/`; timeout được giữ nguyên
  trong dữ liệu.
- Chạy 11 mẫu rerun được ghi trong `results/raw/rerun_log.md`; hoàn thiện
  `bench/analyze.py`, bảng `results/tables/summary.csv` và sáu bộ hình
  PNG/PDF.
- Cập nhật hướng dẫn build, chạy, dữ liệu, phân tích, ngoại lệ validator và
  phân công trong README, `bench/` và `data/`.

## TV2: Generator, định dạng khóa và seed

- Dùng `SplittableRandom` để sinh dữ liệu tất định; seed chính thức là 1, 2, 3.
- Mã hóa ID thành chuỗi 12 ký tự cơ số 36 `[0-9A-Z]`; dùng `LongSet` primitive
  để loại trùng và tạo tập truy vấn không giao với khóa hiện diện.
- Cài đặt checksum SHA-256 theo thứ tự byte của các khóa đã mã hóa và bộ dữ liệu
  clustered cho RQ1.
- Hoàn thiện kiểm tra môi trường và các kiểm thử generator/hash liên quan.

## TV3: Linear Probing với Tombstone

- Cài đặt `lp-tomb` với trạng thái EMPTY/TOMB, tái sử dụng tombstone trong
  insert và hỗ trợ ngưỡng rebuild `tau`.
- Cung cấp thống kê tombstone, empty và rebuild cho các chỉ số RQ2/RQ3.
- Kiểm thử các trường hợp xóa, dò xuyên tombstone, tải đầy, rebuild và
  bộ đếm probe.

## TV4: Workload, đối chứng và kiểm thử RQ2/RQ3

- Xây dựng workload chu kỳ xóa-thêm, sinh truy vấn present/absent và xác minh
  bảng sau workload với oracle.
- Tích hợp phép đo khối workload cùng các pha bảng đối chứng RQ2.
- Bổ sung kiểm thử workload trên sáu cấu hình thuật toán khả dụng, ba seed và
  bốn mức tải; kiểm tra cả dữ liệu không hợp lệ.
- Chuẩn bị bản đóng góp Methods/Results/Threats cho RQ2/RQ3 tại
  `bench/TV4_PAPER_DRAFT.md`; phần mô tả rebuild cần TV3 rà soát trước khi nhập
  vào bản thảo chung.

## TV5: Robin Hood Hashing

- Cài đặt Robin Hood (`rh/base`) và biến thể không early-stop
  (`rh/noearly`, CSV `variant=noearly`).
- Cung cấp DIB mean/max và bộ đếm probe theo đơn vị ô đã xem.
- Kiểm thử oracle thao tác, workload và các invariant của Robin Hood.

## TV6: Linear Probing Backshift và CSV Validator

- Cài đặt `lp-backshift/base` với cơ chế dịch lùi theo Knuth Algorithm R; ghi
  `backshift=knuth-r` trong `params`.
- Xây dựng validator cho schema 32 cột, invariant CSV, output_check và độ biến
  thiên giữa các lượt; hỗ trợ kiểm tra dòng mới nhất bằng `--latest`.
- Bổ sung kiểm thử oracle, cụm quấn vòng, xóa và bộ đếm probe.

## Trạng thái nghiệm thu dữ liệu

- RQ1: 600 dòng pha, 590 `ok` và 10 `timeout`; 4 tiến trình có timeout.
- RQ2/RQ3: 1.584 dòng pha, 1.534 `ok` và 50 `timeout`; 12 tiến trình có
  timeout.
- Validator RQ1 PASS. Validator RQ2/RQ3 báo một ngoại lệ variance ở
  `ctl_present` của cấu hình RQ2 với `C=0`; số liệu gốc được giữ nguyên và giải
  thích trong `results/raw/rerun_log.md` cùng README.
- Kết quả phân tích có trong `results/tables/summary.csv` và sáu bộ hình trong
  `results/figures/`.
