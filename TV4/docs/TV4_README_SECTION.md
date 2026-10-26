## Kiểm thử (TV4)

Chạy ở thư mục gốc dự án (Windows, JDK 1.8):

```
tv4_test.bat                 :: build + chạy mọi cấu hình đã có trong HashIndexFactory
tv4_test.bat --only rh       :: chỉ một cấu hình (sc, lp-tomb, lp-tomb+rebuild, lp-backshift, rh, rh-noearly)
tv4_test.bat --ops 100000    :: rút ngắn oracle khi đang phát triển (chốt thì dùng mặc định 1_000_000)
tv4_test.bat --require-all   :: cấu hình chưa có bị tính là THẤT BẠI (dùng trước khi gắn tag rc1)
tv4_test.bat --self          :: kiểm tra chính harness (bản tham chiếu xanh, 4 bản đột biến phải bị bắt)
```

Cấu hình mà `HashIndexFactory` chưa hỗ trợ được in `SKIP`. Mã thoát: 0 xanh, 1 có FAIL, 2 chưa có cấu hình nào.

Test gồm: ca bảng nhỏ M = 16 (cơ bản, `MIN_VALUE`, bảng đầy, dừng khi đầy, cụm quấn vòng ô 15 → 0, xóa–thêm 20 000 bước, thống kê), oracle 10^6 thao tác với `HashMap` ở ba miền khóa 0,5M / M / 2M, workload RQ2/RQ3 và bảng đối chứng. Cài đặt nên có `public void validate()` (ném `IllegalStateException` khi sai bất biến); harness tự gọi nếu có.

Trace tay để học (không dùng trong vùng đo): khóa đặt tên, `A@7` nghĩa là khóa A có ô nhà 7.

```
java -cp hashbench\out hashbench.test.TraceCli --algo rh --variant base --p 3 put:A@7=1 put:B@7=2 put:C@7=3 put:D@0=4 rm:B get:C
```
