CONVENTIONS.md (nháp)

1. CSV
- 20 cột bắt buộc theo Bảng 4, rồi cột riêng:
  proc_id, phase, wall_ms, phase_note, tomb_ratio, empty_ratio,
  rebuild_count, rebuild_ms, block_median_ms, block_max_ms, dib_mean, dib_max
- 1 dòng = 1 pha đo của 1 tiến trình. run_id = proc_id + "_" + phase.
- status chỉ gồm: ok | timeout | crash | oom | wrong.
- Pha chưa chạy: status = timeout/crash/oom, elapsed_ms trống, phase_note = not_started.
- wall_ms = thời gian cả tiến trình (giống nhau ở mọi pha của cùng proc_id).
- Số ghi vào CSV dùng Locale.US, elapsed_ms 3 chữ số thập phân.

2. Tên thuật toán
- sc, lp-tomb, lp-backshift, rh: variant = base.
- rh-noearly: algorithm = rh, variant = noearly.
- Tham số tau, p, alpha, load, C, queries nằm trong params (key=value;...).

3. Tên pha
- Đã triển khai và chạy được trong code hiện tại: build, present, absent.
- Thuộc kế hoạch RQ2/RQ3, chưa triển khai trong code: workload, ctl_present, ctl_absent.

4. dataset: rq1a, rq1b, rq1clust, rq2, rq3, test
   n = số khóa N.

5. Kỹ thuật
- Hash: (int)((k * 0x9E3779B97F4A7C15L) >>> (64 - p)), chỉ dùng >>>.
- idx = (idx + 1) & mask; DIB = (cur - home) & mask.
- NOT_FOUND = Integer.MIN_VALUE; put với giá trị này ném IllegalArgumentException.
- Generator/oracle/workload không sinh MIN_VALUE (đổi thành 0).
- LongSet dùng -1L làm ô rỗng.
- Lượt --count ghi ops_count; số truy vấn nằm trong params (queries=...).
- RQ3: mốc C thuộc {M, 2M, 4M}.
- Seed: 3 seed cố định được chốt bởi TV2: 1, 2, 3 (khai báo tại Generator.FIXED_SEEDS, lưu tại bench/seeds.txt).