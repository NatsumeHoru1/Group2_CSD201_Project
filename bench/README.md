# Hashbench runner

`spec_notes.txt` chưa có trong workspace tại thời điểm tạo runner. Vì vậy, header
20 cột đầu trong `CsvSchema.java` hiện là `TODO_COLUMN_01` đến
`TODO_COLUMN_20`; không coi đây là schema chính thức. Bốn cột cuối là
`proc_id`, `phase`, `wall_ms`, `phase_note`. Chưa thể ánh xạ status và
elapsed_ms vào 20 cột đầu cho đến khi có Bảng 4 của `spec_notes.txt`; hiện
hai giá trị này được giữ trong `Runner.PhaseResult` và phase log.

`run_id` được tính từ `proc_id + "_" + phase`, không thêm cột thứ 25 vì schema
chỉ yêu cầu thêm bốn cột.

## Dùng

- Chạy `build.bat` ở thư mục gốc dự án để biên dịch bằng Java 8 và tạo
  `hashbench/out/hashbench.jar`.
- Tự kiểm tra: `java -jar hashbench/out/hashbench.jar --selftest`.
- Chạy khung runner: `java -Xmx2g -jar hashbench/out/hashbench.jar --run`.
- Chạy khung runner với cờ đếm: `java -Xmx2g -jar hashbench/out/hashbench.jar --count`.
- Chạy test tiến trình pha: `java -cp hashbench/out hashbench.RunnerPhaseTest`.
- Giới hạn thời gian: `java -Xmx2g -jar hashbench/out/hashbench.jar --run --timeout-sec 5`.

`--run` và `--count` hiện chỉ chạy các pha giả `build`, `present`, `absent`,
`control` ở tiến trình con. Chúng không chạy phép đo và không đại diện cho bất
kỳ thuật toán bảng băm nào. `--sleep-phase` và `--exit-phase` là các hook kiểm
thử, không dùng cho benchmark. Mỗi pha đã xong được append vào
`results/raw/tmp/<proc_id>.phases` rồi đóng file ngay. Runner thu hoạch log sau
khi tiến trình kết thúc hoặc bị hủy do timeout.

`RunConfig` dùng tên trường `rq`, `algo`, `p`, `alpha`, `c`, `tau`, `seed`,
`rep` từ thông tin có trong yêu cầu; định dạng tham số chính thức cần đối chiếu
với `spec_notes.txt`.
