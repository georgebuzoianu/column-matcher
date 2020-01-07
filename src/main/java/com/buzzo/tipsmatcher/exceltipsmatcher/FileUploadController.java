package com.buzzo.tipsmatcher.exceltipsmatcher;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import cc.redberry.combinatorics.Combinatorics;
import com.buzzo.tipsmatcher.exceltipsmatcher.model.Tipster;
import com.buzzo.tipsmatcher.exceltipsmatcher.model.TipstersCombination;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static java.util.stream.Collectors.*;

@Controller
@RequestMapping("/file")
public class FileUploadController {

    private static Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("excelFile") MultipartFile file,
                                   @RequestParam("k") final Integer initK,
                                   @RequestParam("noOfDaysAnalyzed") final Integer NO_OF_DAYS_ANALYZED,
                                   @RequestParam("sortType") final String sortType,
                                   RedirectAttributes redirectAttributes) throws IOException {

//        redirectAttributes.addFlashAttribute("message", "You successfully uploaded " + file.getOriginalFilename() + "!");
        logger.info("============================ START MAGIC SHIT ============================");
        List<Tipster> tipstersList = getTipstersFromFile(file, sortType, NO_OF_DAYS_ANALYZED);
        redirectAttributes.addFlashAttribute("tipsters", tipstersList);

        final Integer k = initK > tipstersList.size() ? tipstersList.size() : initK;

        List<int[]> combinations = Combinatorics.combinations(tipstersList.size(), k).toList();

        logger.info("Total no of possible combinations of {} in sets of {}: {}", tipstersList.size(), k, combinations.size());

        Map<Integer, List<TipstersCombination>> results = combinations.stream().map(combination ->
        {
            List<Tipster> tipstersCombination = new ArrayList<>();
            for (int i = 0; i < combination.length; i++)
                tipstersCombination.add(tipstersList.get(combination[i]));

            return tipstersCombination;
        })
                .map(tipstersCombination -> {
                    int noOfSuccesses = 0;
                    Map<Integer, Integer> winDays = new HashMap<>();
                    for (int i = 1; i <= NO_OF_DAYS_ANALYZED; i++) {
                        final int day = i;
                        String sumString = tipstersCombination.stream().map(tipster -> tipster.getResults().get(day)).reduce("0", (a, b) -> {
                            int acc = Integer.parseInt(a);
                            int valueToAdd = 0;
                            if (b.equals("N"))
                                valueToAdd = 1;
                            else
                                valueToAdd = Integer.parseInt(b);
                            acc = acc + valueToAdd;
                            return String.valueOf(acc);
                        });
                        int sum = Integer.parseInt(sumString);
                        if (sum == k) {
                            noOfSuccesses++;
                            winDays.put(day, 1);
                        }

                    }
                    return Pair.of(noOfSuccesses, TipstersCombination.builder().tipsters(tipstersCombination).winDays(winDays).build());
                })
                .filter(pair -> pair.getLeft() > 1)
                .collect(groupingBy(Pair::getLeft, Collectors.mapping(Pair::getRight, Collectors.toList())));

        logger.info("Computation DONE!");

        LinkedHashMap<Integer, List<TipstersCombination>> resultsSorted = results.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        logger.info("Sorting results DONE!");

        resultsSorted.entrySet().stream().forEach(r -> {
                    logger.info("Results: {} wins/month -> {} combinations ", r.getKey(), r.getValue().size());
                }
        );

        if (resultsSorted.entrySet().size() > 2) {
            Iterator<Map.Entry<Integer, List<TipstersCombination>>> iterator = resultsSorted.entrySet().iterator();
            Map.Entry<Integer, List<TipstersCombination>> firstSetOfWinningCombinations = iterator.next();
            Map.Entry<Integer, List<TipstersCombination>> secondSetOfWinningCombinations = iterator.next();
           logger.info("Keep only combinations for {} wins/month and {} wins/month", firstSetOfWinningCombinations.getKey(), secondSetOfWinningCombinations.getKey());
            resultsSorted = new LinkedHashMap<>();
            resultsSorted.put(firstSetOfWinningCombinations.getKey(), firstSetOfWinningCombinations.getValue());
            resultsSorted.put(secondSetOfWinningCombinations.getKey(), secondSetOfWinningCombinations.getValue());
        }

        logger.info("Filtering results DONE!");

        int noOfFoundCombinations = resultsSorted.values().stream().mapToInt(list -> list.size()).sum();

        redirectAttributes.addFlashAttribute("noOfFoundCombinations", noOfFoundCombinations);
        redirectAttributes.addFlashAttribute("resultsSorted", resultsSorted);
        redirectAttributes.addFlashAttribute("days", getDaysArray(NO_OF_DAYS_ANALYZED));
        logger.info("============================ MAGIC SHIT IS READY ============================");
        return "redirect:/";
    }

    private List<Tipster> getTipstersFromFile(MultipartFile file, String sortType, Integer NO_OF_DAYS_ANALYZED) throws IOException {
        List<Tipster> tipstersList = new ArrayList<Tipster>();
        XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream());
        XSSFSheet worksheet = workbook.getSheetAt(0);

        for (int rowNum = 1; rowNum < worksheet.getPhysicalNumberOfRows(); rowNum++) {

            XSSFRow row = worksheet.getRow(rowNum);

            if (row == null)
                continue;

            XSSFCell tipsterNameCell = row.getCell(0);

            if (tipsterNameCell == null || tipsterNameCell.getCellType() != CellType.STRING)
                continue;

            String tipsterName = tipsterNameCell.getStringCellValue();

            Tipster tipster = Tipster.builder()
                    .name(tipsterName)
                    .results(new HashMap())
                    .build();
            int winsPerMonth = 0;
            for (int day = 1; day <= NO_OF_DAYS_ANALYZED; day++) {

                int intResultOfDay = 0;
                String resultOfDay = "0";
                if (row.getCell(day) != null && row.getCell(day).getCellType() == CellType.NUMERIC) {
                    intResultOfDay = (int) row.getCell(day).getNumericCellValue();
                    resultOfDay = String.valueOf(intResultOfDay);
                }

                if (row.getCell(day) != null && row.getCell(day).getCellType() == CellType.STRING && row.getCell(day).getStringCellValue().equals("N")) {
                    //if tip is missing for the day I want to count ZERO
                    intResultOfDay = 0;
                    resultOfDay = "N";
                }


                tipster.getResults().put(day, resultOfDay);
                winsPerMonth += intResultOfDay;
            }
            tipster.setWinsPerMonth(winsPerMonth);
            tipster.setPreviousWins((int) row.getCell(NO_OF_DAYS_ANALYZED + 2).getNumericCellValue());
            tipster.setTotalWins(tipster.getWinsPerMonth() + tipster.getPreviousWins());
            tipstersList.add(tipster);
        }
        logger.info("File reading DONE!");

        logger.info("Tipsters found: {} and sort type: {}", tipstersList.size(), sortType);

        Comparator comparator = "month".equals(sortType) ?
                Comparator.comparing(Tipster::getWinsPerMonth).reversed()
                : Comparator.comparing(Tipster::getTotalWins).reversed();
        tipstersList.sort(comparator);

        if (tipstersList.size() > 25) {
            tipstersList = tipstersList.subList(0, 25);
            logger.info("Keep only the first 25 tipsters");
        }
        return tipstersList;
    }


    private List<Integer> getDaysArray(Integer noOfDays) {
        List<Integer> days = new ArrayList<>();
        for (int i = 1; i <= noOfDays; i++) {
            days.add(i);
        }
        return days;
    }
}

//    @ExceptionHandler(StorageFileNotFoundException.class)
//    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
//        return ResponseEntity.notFound().build();
//    }

