package com.example.CloudServer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardMetricsDto {
    private Long activeSize;    // Dung lượng file đang sử dụng
    private Long trashSize;     // Dung lượng file trong thùng rác
    private Long transferToday; // Dung lượng upload trong ngày
}