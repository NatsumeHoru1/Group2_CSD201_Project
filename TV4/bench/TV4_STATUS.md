# TV4 STATUS (dán vào TEAM_SYNC.md)

**Đã xong, chạy được trên main sau khi merge PR của TV4:**
- Test harness tự chứa: `java -cp hashbench\out hashbench.test.AllTests` (hoặc `tv4_test.bat`). Nối qua `HashIndexFactory`; cấu hình nào factory chưa hỗ trợ thì in `SKIP`, thêm nhánh vào factory là tự được test, không cần sửa harness.
- Oracle 10^6 thao tác (put/get/remove = 40/40/20, M = 2^12, miền khóa 0,5M / M / 2M), gọi `validate()` bằng reflection nếu lớp có.
- Bảy ca bảng nhỏ M = 16: cơ bản, MIN_VALUE, bảng đầy (FULL không đổi dữ liệu), dừng khi đầy, cụm quấn vòng 15→0, xóa–thêm 20 000 bước, thống kê; cộng ca khớp `Hashing` của TV2 với bản băm độc lập.
- Workload RQ2/RQ3 (`hashbench.workload.Workload`: pool N+C khóa, dãy khóa xóa, khối 10^4 lệnh, `BlockListener` để ghi log từng khối) và bảng đối chứng (`ControlTable`). `output_check` theo đúng quy ước nhóm.
- `hashbench.test.TraceCli`: in bảng sau từng thao tác để luyện trace tay (tài liệu trong `docs/`).
- `--self`: kiểm tra chính harness (bản tham chiếu xanh, 4 bản đột biến phải bị bắt).

**Kết quả hiện tại:** `rh` và `rh-noearly` (TV5): 13/13 test mỗi cấu hình PASS, gồm oracle 10^6 thao tác ở cả 3 miền.
`sc`, `lp-tomb`, `lp-tomb+rebuild`, `lp-backshift`: SKIP vì `HashIndexFactory` chưa có nhánh.

**Chờ người khác:**
- TV2 (`sc`), TV3 (`lp-tomb`), TV6 (`lp-backshift`): thêm nhánh vào `HashIndexFactory`, rồi chạy `tv4_test.bat --only <tên>`.
- TV3: chốt chữ ký `create(algo, variant, p, tau)`. Registry đã tự nhận cả 3 và 4 tham số; `lp-tomb+rebuild` (tau = 0,2) chỉ chạy khi có chữ ký 4 tham số.
- Muốn test rebuild/TOMB: `lp-tomb` implement `HashStats` (kèm `public void validate()`).

**Chưa làm được:** 6 ca bắt buộc theo đúng thứ tự của đề (spec_notes.txt không chép phần này). Cần file đề.

**Cần chốt (ghi vào CONVENTIONS.md):** "khối 10^4 thao tác" = 10^4 lệnh remove/put (5 000 cặp) hay 10^4 cặp?; "40/40/20" = put/get/remove.

**Chưa nối vào Main/Measure:** pha `workload`, `ctl_present`, `ctl_absent` (hiện `Main.runMeasureChild` chỉ nhận rq1a/rq1b và 3 pha). TV4 cung cấp `Workload.execute(...)`, `ControlTable.build(...)`, `QueryBench`; TV1 mở rộng `Main/Measure` gọi vào các hàm này.
