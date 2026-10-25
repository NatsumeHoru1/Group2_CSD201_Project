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

* TV2: [HOÀN THÀNH] (1) Đã sửa generateClusteredKeys đúng đặc tả: 10% khóa có h(k) < M/64, 90% phân bố đều. (2) computeSHA256 đã chuyển sang bảng tra cứu hex tĩnh bytesToHex() O(1). (3) Đã thêm verify_env.bat kiểm tra môi trường Java 8 / bytecode 52 / GC / selftest. (4) Đã chốt 3 seed: 1, 2, 3 (khai báo tại Generator.FIXED_SEEDS và bench/seeds.txt).
* TV3: `lp-tomb` (EMPTY/TOMB, put nhớ TOMB đầu tiên, dò tối đa M ô), rebuild theo tau. Đẩy lên main sớm nhất có thể và báo ngày giao.
* TV4: test harness + oracle 10^6 thao tác + 6 ca bắt buộc, báo trạng thái. Workload RQ2/RQ3 phụ thuộc lp-tomb.
* TV5: rh đã dùng được. Chạy `validate()` sau oracle 10^6 thao tác và báo kết quả. Hỗ trợ lp-backshift nếu TV6 chậm.
* TV6: `lp-backshift` (ghi rõ Algorithm R hay chèn lại cụm vào params), validator CSV theo 32 cột trong `CsvSchema.java`.

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

