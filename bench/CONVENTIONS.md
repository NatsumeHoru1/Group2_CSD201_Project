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
- Chạy: java -cp hashbench\out hashbench.CsvValidator [--latest] <file.csv> [file2.csv ...]
- Exit code 0 = PASS, 1 = có vi phạm, 2 = thiếu tham số.
- Cờ --latest: khi file có các dòng trùng run_id do chạy lại crash/wrong, chỉ giữ dòng cuối cùng của mỗi run_id trước khi kiểm tra, không sửa file CSV gốc.
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

7. Quy ước proc_id, Resume, Batch và Rerun (TV1)
- Dạng proc_id tất định: <rq>_<algo>_<variant>_<dataset>_p<p>_a<alpha>_t<tau>_s<seed>_r<repeat>[-count].
  Các số thực alpha và tau bỏ dấu chấm bằng chữ "p" (ví dụ: a0p75, t0p2; tau vô hạn viết tinf). Lượt --count có hậu tố "-count".
- Chính sách --resume: Một tiến trình được coi là hoàn tất hợp lệ khi đủ mọi pha yêu cầu và tất cả các pha có status thuộc {ok, timeout, oom}. Khi gặp cấu hình đã hoàn tất, runner bỏ qua (in "SKIP <proc_id>"). Nếu cấu hình có pha bị crash/wrong hoặc thiếu pha, runner chạy lại và chỉ nối thêm dòng mới vào CSV (không xóa dòng cũ; dùng CsvValidator --latest để nghiệm thu).
- Dataset rq1clust (bộ dồn cụm RQ1): Khóa có mặt sinh bằng Generator.generateClusteredKeys (>=10% khóa có h(k) < M/64). Khóa vắng mặt sinh theo phân phối đều độc lập bằng Generator.generateAbsentKeys và loại bỏ các khóa có mặt trong tập dồn cụm.
- Ma trận RQ1 200 tiến trình:
  + 8 cấu hình cơ sở: Thí nghiệm A (rq1a, p=20, alpha thuộc {0.50, 0.75, 0.90, 0.95}). Thí nghiệm B (rq1b, alpha=0.75, p thuộc {18, 21, 22, 23}; riêng điểm p=20 alpha=0.75 dùng chung với thí nghiệm A, không sinh dòng rq1b p=20).
  + 144 dòng thời gian: 8 cấu hình x 2 thuật toán (sc, lp-tomb tau=inf) x 3 seed x 3 repeat.
  + 48 dòng count: 8 cấu hình x 2 thuật toán x 3 seed x repeat=1 (proc_id có -count).
  + 8 dòng dồn cụm (rq1clust, alpha=0.75, seed=1, repeat=1): p=18 và p=20, 2 thuật toán x {thời gian, count}.
  + Thứ tự xen kẽ xác định: đảo thứ tự 2 thuật toán khi (repeat + seed) lẻ, không có 2 dòng liên tiếp cùng (cấu hình, thuật toán).
- Lệnh Batch và Rerun:
  + Sinh ma trận: java -jar hashbench.jar --batch-gen-rq1 --out bench\batch_rq1.txt
  + Chạy batch: java -jar hashbench.jar --batch bench\batch_rq1.txt --out <csv> [--log <file>] [--limit N] [--official]
  + Chạy lại kiểm tra (< 60s): java -jar hashbench.jar --rerun <run_id> --csv <file> (so khớp theo tiêu chuẩn mục 10.1: status và output_check giống nhau, thời gian trong 0.5-2.0x median).
