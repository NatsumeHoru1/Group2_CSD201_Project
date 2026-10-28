# Nhật Ký Kiểm Chứng Rerun (Validation Log - Criterion 10.1)

| run_id | Phase | Status Gốc | Status Re-run | Checksum Gốc | Checksum Re-run | Elapsed Gốc (ms) | Elapsed Re-run (ms) | Tỷ lệ | Kết quả |
|---|---|---|---|---|---|---|---|---|---|
| RQ1_sc_base_rq1b_p23_a0p75_tinf_s1_r1_present | present | ok | ok | 15724331603734 | 15724331603734 | 2256.95 | 2243.726 | 0.994x | ĐẠT (PASS) |
| RQ1_sc_base_rq1b_p23_a0p75_tinf_s1_r1_absent | absent | ok | ok | 5000000 | 5000000 | 1241.595 | 1299.51 | 1.047x | ĐẠT (PASS) |
| RQ3_lp-tomb_base_rq3_p20_a0p0_t0p1_l0p8_c1048576_s1_r1_build | build | ok | ok |  |  | 142.818 | 150.202 | 1.052x | ĐẠT (PASS) |
| RQ1_sc_base_rq1a_p20_a0p5_tinf_s2_r2_present | present | ok | ok | 1310162377811 | 1310162377811 | 1250.133 | 1334.41 | 1.067x | ĐẠT (PASS) |
| RQ1_lp-tomb_base_rq1b_p18_a0p75_tinf_s2_r2_absent | absent | ok | ok | 5000000 | 5000000 | 1606.166 | 1295.968 | 0.807x | ĐẠT (PASS) |
| RQ1_lp-tomb_base_rq1b_p21_a0p75_tinf_s3_r2_absent | absent | ok | ok | 5000000 | 5000000 | 2222.525 | 2628.666 | 1.183x | ĐẠT (PASS) |
| RQ1_lp-tomb_base_rq1a_p20_a0p75_tinf_s3_r2_absent | absent | ok | ok | 5000000 | 5000000 | 2037.501 | 2051.701 | 1.007x | ĐẠT (PASS) |
| RQ3_lp-tomb_base_rq3_p20_a0p0_t0p2_l0p5_c4194304_s3_r2_build | build | ok | ok |  |  | 51.94 | 67.614 | 1.302x | ĐẠT (PASS) |
| RQ3_rh_noearly_rq3_p20_a0p0_tinf_l0p8_c1048576_s3_r3_present | present | ok | ok | 3790620943636 | 3790620943636 | 333.987 | 365.121 | 1.093x | ĐẠT (PASS) |
| RQ3_lp-tomb_base_rq3_p20_a0p0_t0p2_l0p5_c1048576_s3_r1_present | present | ok | ok | 2119394928336 | 2119394928336 | 260.026 | 273.512 | 1.052x | ĐẠT (PASS) |
| RQ3_lp-tomb_base_rq3_p20_a0p0_t0p1_l0p8_c1048576_s1_r1_absent | absent | ok | ok | 1000000 | 1000000 | 1030.49 | 1149.622 | 1.116x | ĐẠT (PASS) |

## Ghi chú ngoại lệ variance trong dữ liệu RQ2/RQ3

`CsvValidator --latest` hiện báo một ngoại lệ ở pha `ctl_present` của
`RQ2_lp-tomb_base_rq2_p20_a0p0_tinf_l0p5_c0_s1_r1`: `elapsed_ms=314.931`,
trong khi median của nhóm là `152.481 ms` (tỷ lệ `2.065x`, vượt ngưỡng `2.0x`).
Đây là một outlier về thời gian ở lượt đầu của cấu hình/tiến trình, có khả năng
do biến động JIT hoặc cold-start của JVM và nhiễu hệ thống. Dòng có
`status=ok`; các phép đo kiểm tra tính đúng đắn không cho thấy sai lệch logic,
nên ngoại lệ này được xem là vấn đề thời gian đo chứ không phải lỗi kết quả.

Ngoại lệ vẫn là vi phạm ngưỡng variance của validator và không được đánh dấu
PASS. Giữ nguyên dữ liệu gốc trong `results/raw/official/rq23_results.csv`;
không xóa, sửa hoặc thay thế dòng đo để làm sạch kết quả.

Đã chạy lại đúng `run_id` này vào ngày 2026-10-28: `status=ok` và
`output_check=1039106476494` khớp dòng gốc; thời gian mới là `157.419 ms`.
Lệnh rerun dùng median `150.893 ms` của 9 lượt cùng cấu hình làm tham chiếu,
nên tỷ lệ rerun là `1.043x` và kết luận rerun là KHỚP. Kết quả này hỗ trợ cách
diễn giải đây là outlier thời gian đơn lẻ (phù hợp với nhiễu JIT/cold-start),
nhưng không chứng minh nguyên nhân cụ thể và không xóa vi phạm của số đo gốc
trong validator.

Hai median khác nhau vì cách nhóm mẫu khác nhau: validator kiểm tra variance
theo từng seed và không tách dòng timed khỏi dòng `--count`, còn `--rerun` dùng
các dòng timed cùng cấu hình trên 9 lượt/seed. Vì vậy rerun KHỚP là bằng chứng
bổ sung về tính ổn định của lần chạy mới, không thay đổi kết quả kiểm validator
trên tập CSV đã niêm phong.
