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
- lp-backshift phải ghi rõ cơ chế xóa trong params: backshift=knuth-r (Algorithm R của
  Knuth) hoặc backshift=sedgewick-wayne (chèn lại cụm). Nhóm chốt dùng Algorithm R.
- Cột params của lp-backshift do CsvParams.augment() nối thêm, không sửa các cặp có sẵn.

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
- Seed: 3 seed cố định, commit vào repo (xem seeds.txt; khai báo tại Generator.FIXED_SEEDS).

6. Validator CSV (TV6)
- Chạy: java -cp hashbench\out hashbench.CsvValidator <file.csv> [file2.csv ...]
- Exit code 0 = PASS, 1 = có vi phạm, 2 = thiếu tham số.
- Kiểm: header đúng 32 cột đúng thứ tự; mỗi hàng đủ 32 ô; status thuộc
  {ok, timeout, crash, oom, wrong}; lượt ok có elapsed_ms dạng số 3 chữ số lẻ,
  lượt không ok để trống elapsed_ms; phase_note=not_started chỉ đi kèm
  timeout/crash/oom; run_id = proc_id + "_" + phase và không trùng; algorithm/variant/
  dataset/phase thuộc danh sách cho phép; output_check bằng nhau giữa các thuật toán
  cùng (dataset, seed, phase); mỗi nhóm RQ1 có đủ build/present/absent;
  lp-backshift phải có backshift trong params; input_sha256 là 64 ký tự hex;
  độ dao động giữa các lượt của cùng cấu hình nằm trong 0,5 - 2 lần median
  (theo C4 và quy định 10.1).
- Validator chỉ đọc file, không sửa dòng nào trong results/raw/.
