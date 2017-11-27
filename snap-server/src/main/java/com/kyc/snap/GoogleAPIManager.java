
package com.kyc.snap;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.stream.Collectors;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.DimensionProperties;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateDimensionPropertiesRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class GoogleAPIManager {

    private static final String APPLICATION_NAME = "Snap";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private Sheets sheets;

    GoogleAPIManager(String credentialsFile) {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(credentialsFile))
                .createScoped(ImmutableSet.of(DriveScopes.DRIVE));
            sheets = new Sheets.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    String exportToGoogleSheets(ParsedGrid parsedGrid) {
        try {
            // TODO create new sheet
            Spreadsheet spreadsheet = sheets.spreadsheets().get("1n2XG8kgi-XZoD1n5jZoW4UbIFI99U2l0Uc_9SQPb8TA").execute();
            int sheetId = 0;

            sheets.spreadsheets()
                .batchUpdate(spreadsheet.getSpreadsheetId(), new BatchUpdateSpreadsheetRequest()
                    .setRequests(ImmutableList.of(new Request()
                        .setUpdateCells(new UpdateCellsRequest()
                            .setFields("userEnteredFormat")
                            .setRange(new GridRange()
                                .setSheetId(sheetId))), new Request()
                        .setUpdateDimensionProperties(new UpdateDimensionPropertiesRequest()
                            .setProperties(new DimensionProperties()
                                .setPixelSize(20))
                            .setFields("pixelSize")
                            .setRange(new DimensionRange()
                                .setSheetId(sheetId)
                                .setDimension("COLUMNS"))))))
                .execute();

            sheets.spreadsheets()
                .batchUpdate(spreadsheet.getSpreadsheetId(), new BatchUpdateSpreadsheetRequest()
                    .setRequests(parsedGrid.getSquares().stream()
                        .map(square -> {
                            java.awt.Color color = new java.awt.Color(square.getRgb());
                            return new Request()
                                .setUpdateCells(new UpdateCellsRequest()
                                    .setRows(ImmutableList.of(new RowData()
                                        .setValues(ImmutableList.of(new CellData()
                                            .setUserEnteredFormat(new CellFormat()
                                                .setBackgroundColor(new Color()
                                                    .setRed(color.getRed() / 255f)
                                                    .setGreen(color.getGreen() / 255f)
                                                    .setBlue(color.getBlue() / 255f)))))))
                                    .setFields("userEnteredFormat.backgroundColor")
                                    .setRange(new GridRange()
                                        .setSheetId(sheetId)
                                        .setStartRowIndex(square.getRow())
                                        .setEndRowIndex(square.getRow() + 1)
                                        .setStartColumnIndex(square.getCol())
                                        .setEndColumnIndex(square.getCol() + 1)));
                        })
                        .collect(Collectors.toList())))
                .execute();

            return spreadsheet.getSpreadsheetUrl();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
