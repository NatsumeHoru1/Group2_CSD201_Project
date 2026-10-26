# Luyện trace tay (kiểm tra cá nhân, 5 phút, không AI)

Tình huống chuẩn trên **M = 8**: A, B, C cùng ô nhà 7; D có ô nhà 0. Thao tác: put A, put B, put C, put D, remove B, get C, get D. (Cụm quấn vòng từ ô 7 sang ô 0, 1, 2.)

Chạy bằng máy để đối chiếu: `java -cp hashbench\out hashbench.test.TraceCli --algo <tên> --variant base --p 3 put:A@7=1 put:B@7=2 put:C@7=3 put:D@0=4 rm:B get:C get:D` (khóa cụ thể do công cụ chọn; chỉ ô nhà quan trọng).

## Kết quả máy cho rh (đã chạy thật trên RobinHoodTable)
```
> rm:B  ->  true   (size=3)
0: key=C(21) dib=1
1: key=D(0) dib=1
2: -
3: -
4: -
5: -
6: -
7: key=A(8) dib=0

> get:C  ->  3   (size=3)
```
Trước khi xóa B (đã chạy thật): ô 7 = A (dib 0), ô 0 = B (dib 1), ô 1 = C (dib 2), ô 2 = D (dib 2).

## Tự trả lời trước, rồi mới xem đáp án
Với từng thuật toán, vẽ bảng 8 ô sau từng thao tác và ghi số ô đã xem của `get C`, `get D` sau khi xóa B.

| Thuật toán | Việc phải nói được |
|---|---|
| lp-tomb | Ô 0 thành TOMB; `get` bỏ qua TOMB và dò tiếp; `put` nhớ TOMB đầu tiên nhưng vẫn dò hết cụm để chắc khóa chưa có |
| lp-backshift | Dịch lùi theo Algorithm R: phần tử có ô nhà *không* nằm trong khoảng chu kỳ (i, j] thì được chuyển vào ô trống |
| rh | Dịch lùi và giảm dib của từng phần tử đến khi gặp ô trống hoặc dib = 0 |
| rh-noearly | Giống rh nhưng `get` không được dừng sớm khi dib ô < khoảng cách đã đi |
| sc | Xóa node khỏi danh sách của bucket 7 (thứ tự phụ thuộc chèn đầu hay cuối; xem code của TV2) |

## Đáp án tham khảo (tính tay; riêng rh có máy xác nhận)
Sau `remove B`, bảng của **rh, lp-backshift**: ô 7 = A, ô 0 = C, ô 1 = D, các ô còn lại trống; `get C` xem 2 ô (7, 0), `get D` xem 2 ô (0, 1).
Bảng của **lp-tomb**: ô 7 = A, ô 0 = TOMB, ô 1 = C, ô 2 = D; `get C` xem 3 ô (7, 0, 1), `get D` xem 3 ô (0, 1, 2).
Đáp án của lp-backshift và lp-tomb tính tay từ định nghĩa, chưa có máy xác nhận vì chưa có code; chạy `TraceCli` khi TV3, TV6 nộp để kiểm.

## Sửa nhỏ + chạy test (luyện)
1. Đổi `TAU` trong `Registry` từ 0,2 sang 0,1 rồi chạy lại.
2. Đổi tỷ lệ 40/40/20 thành 50/30/20 trong `OracleTest`.
3. Thêm một ca vào `CoreCases` (ví dụ xóa phần tử cuối cụm quấn vòng).
4. Thêm một bản đột biến vào `BuggyIndex` và xác nhận `--self` bắt được.
