package com.badminton.service;

import com.badminton.dto.response.SignupVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivitySignupExcelServiceTest {
    private final ActivitySignupExcelService excelService = new ActivitySignupExcelService();

    @Test
    void exportCreatesWorkbookWithActivityAndSignupDetails() throws Exception {
        SignupVO signup = new SignupVO();
        signup.setNickname("羽球爱好者");
        signup.setName("张三");
        signup.setPhone("13800138000");

        byte[] bytes = excelService.export("周末羽毛球赛", Collections.singletonList(signup));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("报名名单");
            assertEquals("周末羽毛球赛 - 报名名单", sheet.getRow(0).getCell(0).getStringCellValue());

            Row header = sheet.getRow(1);
            assertEquals("序号", header.getCell(0).getStringCellValue());
            assertEquals("昵称", header.getCell(1).getStringCellValue());
            assertEquals("联系人姓名", header.getCell(2).getStringCellValue());
            assertEquals("联系电话", header.getCell(3).getStringCellValue());

            Row data = sheet.getRow(2);
            assertEquals(1, (int) data.getCell(0).getNumericCellValue());
            assertEquals("羽球爱好者", data.getCell(1).getStringCellValue());
            assertEquals("张三", data.getCell(2).getStringCellValue());
            assertEquals("13800138000", data.getCell(3).getStringCellValue());
        }
    }

    @Test
    void exportCreatesTitleAndHeadersForEmptySignupList() throws Exception {
        byte[] bytes = excelService.export("无人报名活动", Collections.emptyList());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("报名名单");
            assertEquals("无人报名活动 - 报名名单", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("序号", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(1, sheet.getLastRowNum());
        }
    }
}
