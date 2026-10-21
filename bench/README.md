# Hashbench runner

`spec_notes.txt` chưa có trong workspace tại thời điểm tạo runner. Vì vậy, header
20 cột trong `CsvSchema.java` hiện là `TODO_COLUMN_01` đến `TODO_COLUMN_20`;
không coi đây là schema chính thức. Hãy thay các tên này theo Bảng 4 của
`spec_notes.txt` khi nhận được tài liệu.

## Dùng

- Chạy `build.bat` ở thư mục gốc dự án để biên dịch bằng Java 8 và tạo
  `hashbench/out/hashbench.jar`.
- Tự kiểm tra: `java -jar hashbench/out/hashbench.jar --selftest`.
- Chạy khung runner: `java -Xmx2g -jar hashbench/out/hashbench.jar --run`.
- Chạy khung runner với cờ đếm: `java -Xmx2g -jar hashbench/out/hashbench.jar --count`.

`--run` và `--count` hiện chỉ khởi chạy kiểm tra `StubHashIndex` ở tiến trình
con. Chúng không chạy phép đo và không đại diện cho bất kỳ thuật toán bảng băm
nào. `RunConfig` dùng tên trường `rq`, `algo`, `p`, `alpha`, `c`, `tau`, `seed`,
`rep` từ thông tin có trong yêu cầu; định dạng đối số chính thức cần đối chiếu
với `spec_notes.txt`.
