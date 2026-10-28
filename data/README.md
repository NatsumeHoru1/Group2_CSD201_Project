# Dữ liệu benchmark

## Generator và seed

Generator dùng `java.util.SplittableRandom`. Ba seed cố định thực sự được khai
báo trong `Generator.FIXED_SEEDS` và `bench/seeds.txt` là:

| Nhãn lượt | Giá trị seed |
|---|---:|
| `s1` | `1` |
| `s2` | `2` |
| `s3` | `3` |

**Lưu ý:** một ghi chú cũ nhắc tới seed 101/102/103, nhưng mã nguồn, file seed và
CSV chính thức đều dùng 1/2/3. Tài liệu này ghi nhận giá trị đã dùng thực tế;
không được diễn giải các dòng dữ liệu đã đo như thể chúng được tạo bằng
101/102/103.

## Quy cách khóa

Biến thể khóa B mã hóa ID thành chuỗi có đúng 12 ký tự từ bảng
`[0-9A-Z]` (cơ số 36), đệm số 0 ở đầu khi cần. Generator loại khóa trùng bằng
`LongSet`, một tập `long` primitive không boxing. Tập khóa có mặt và tập khóa
vắng mặt được tạo tất định theo seed; các khóa vắng mặt không giao với tập có
mặt.

Thứ tự 12 ký tự của khóa là từ chữ số cơ số 36 có trọng số cao đến thấp. Tuy
nhiên, `input_sha256` không băm mảng số nguyên nhị phân hay cách serialize
endianness cụ thể. `Generator.computeSHA256` băm byte ASCII/UTF-8 của các ký tự
khóa theo thứ tự, nối các khóa có mặt trước rồi các truy vấn vắng mặt sau. Với
RQ2/RQ3, thứ tự là `workload.pool()` tiếp theo là
`workload.absentQueries()`.

## Dữ liệu thô chính thức

Các CSV đã niêm phong được lưu trong `results/raw/official/`:

- `rq1_results.csv`: 200 tiến trình RQ1, một dòng cho mỗi pha.
- `rq23_results.csv`: 360 tiến trình RQ2/RQ3, một dòng cho mỗi pha.

Không chỉnh sửa, lọc bỏ hoặc ghi đè các CSV nguồn để giải quyết timeout hay
ngoại lệ validator. Phân tích được tạo bằng `bench/analyze.py` và xuất ra
`results/tables/` cùng `results/figures/`. Thông tin máy đo nằm ở
`results/raw/machine.md`.
