# PHAN_CONG.md — mục của TV4

Quy tắc: ghi theo việc cụ thể kèm PR/commit, không ghi phần trăm, **không điền hồi tố** (chỉ ghi ngày thật đã làm, hash thật từ `git log`). Các ô `<...>` điền khi xong.

## Việc đứng tên của TV4
- Test harness tự chứa, `Registry` nối `HashIndexFactory`, ca bảng nhỏ M = 16, kiểm tra đột biến.
- Oracle 10^6 thao tác đối chiếu `HashMap`, ba miền khóa.
- Workload RQ2/RQ3 (pool N + C khóa, dãy khóa xóa, khối 10^4 lệnh), bảng đối chứng, `QueryBench`.
- 6 ca bắt buộc theo đề (chờ đề).
- Trace CLI để luyện kiểm tra cá nhân.

## Nhật ký

| Ngày | Việc | PR / commit | Người review |
|---|---|---|---|
| <dd/mm> | Harness + Registry + ca M = 16 | <#PR> / <hash> | <TV5 hoặc TV6> |
| <dd/mm> | Oracle 10^6 thao tác | <#PR> / <hash> | |
| <dd/mm> | Workload + ControlTable + QueryBench | <#PR> / <hash> | |
| <dd/mm> | 6 ca bắt buộc theo đề | <#PR> / <hash> | |
| <dd/mm> | Chạy đủ 6 cấu hình bằng JDK 1.8, tag rc1 | <tag> / <hash> | |

## Review đã làm cho người khác
| Ngày | PR | Nhận xét chính |
|---|---|---|
| <dd/mm> | <#PR của TV5/TV6> | <...> |
