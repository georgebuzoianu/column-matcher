package com.buzzo.tipsmatcher.exceltipsmatcher;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import cc.redberry.combinatorics.Combinatorics;
import com.buzzo.tipsmatcher.exceltipsmatcher.model.Tipster;
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
                                   @RequestParam("sortType") final String sortType,
                                   RedirectAttributes redirectAttributes) throws IOException {

//        redirectAttributes.addFlashAttribute("message", "You successfully uploaded " + file.getOriginalFilename() + "!");

        List<Tipster> tipstersList = getTipstersFromFile(file, sortType);
        redirectAttributes.addFlashAttribute("tipsters" , tipstersList);

        final Integer k = initK > tipstersList.size() ? tipstersList.size() : initK;

        List<int[]> combinations = Combinatorics.combinations(tipstersList.size(), k).toList();

        logger.info("Total no of possible combinations of {} in sets of {}: {}", tipstersList.size(), k, combinations.size());

        List<List<Tipster>> allCombinations = combinations.stream().map(combination ->
        {
            List<Tipster> tipstersCombination = new ArrayList<>();
            for(int i=0; i < combination.length; i++)
                tipstersCombination.add(tipstersList.get(combination[i]));

            return tipstersCombination;
        }).collect(toList());

        Map<Integer, List<List<Tipster>>> results = allCombinations.stream().map(tipstersCombination -> {
            int noOfSuccesses = 0;
            for (int i = 1; i <= 31; i++) {
                final int day = i;
                int sum = tipstersCombination.stream().map(tipster -> tipster.getResults().get(day)).reduce(0, (a, b) -> a + b);
                if(sum == k )
                    noOfSuccesses ++;
            }
            return Pair.of(noOfSuccesses, tipstersCombination);
        })
        .filter(pair -> pair.getLeft() > 1)
        .collect(groupingBy(Pair::getLeft, Collectors.mapping(Pair::getRight, Collectors.toList())));

        LinkedHashMap<Integer, List<List<Tipster>>> resultsSorted = results.entrySet().stream()
                                                        .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        resultsSorted.entrySet().stream().forEach( r -> {
            logger.info("Results: {} wins/month -> {} combinations ", r.getKey(), r.getValue().size());
            }
        );

        Integer lastKey = resultsSorted.keySet().stream().reduce((first, second) -> second)
                .orElse(null);



        if (resultsSorted.entrySet().size() > 2) {
            logger.info("Remove combinations for {} wins/month. They are too many", lastKey);
            resultsSorted.remove(lastKey);
            resultsSorted.entrySet().stream().forEach( r -> {
                        logger.info("FINAL RESULTS: {} wins/month -> {} combinations ", r.getKey(), r.getValue().size());
                    }
            );
        }


        int noOfFoundCombinations =  resultsSorted.values().stream().mapToInt(list -> list.size()).sum();

        redirectAttributes.addFlashAttribute("noOfFoundCombinations" , noOfFoundCombinations);
        redirectAttributes.addFlashAttribute("resultsSorted" , resultsSorted);
        return "redirect:/";
    }

    private List<Tipster> getTipstersFromFile(MultipartFile file, String sortType) throws IOException {
        List<Tipster> tipstersList = new ArrayList<Tipster>();
        XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream());
        XSSFSheet worksheet = workbook.getSheetAt(0);

        for (int rowNum = 1; rowNum < worksheet.getPhysicalNumberOfRows(); rowNum++) {


            XSSFRow row = worksheet.getRow(rowNum);

            XSSFCell tipsterNameCell = row.getCell(0);

            if(tipsterNameCell == null || tipsterNameCell.getCellType() != CellType.STRING)
                continue;

            String tipsterName = tipsterNameCell.getStringCellValue();

            Tipster tipster = Tipster.builder()
                    .name(tipsterName)
                    .results(new HashMap())
                    .build();
            int winsPerMonth = 0;
            for (int day = 1; day <= 31; day++) {

                int resultOfDay = 0;
                if(row.getCell(day) !=null && row.getCell(day).getCellType()== CellType.NUMERIC)
                    resultOfDay = (int)row.getCell(day).getNumericCellValue();

                tipster.getResults().put(day, resultOfDay);
                winsPerMonth += resultOfDay;
            }
            tipster.setWinsPerMonth(winsPerMonth);
            tipster.setPreviousWins((int)row.getCell(33).getNumericCellValue());
            tipster.setTotalWins(tipster.getWinsPerMonth() + tipster.getPreviousWins());
            tipstersList.add(tipster);
        }

        logger.info("Tipsters found: {} and sort type: {}", tipstersList.size(), sortType);

        Comparator comparator = "month".equals(sortType) ?
                                        Comparator.comparing(Tipster::getWinsPerMonth).reversed()
                                      : Comparator.comparing(Tipster::getTotalWins).reversed();
        tipstersList.sort(comparator);

        if(tipstersList.size() > 22) {
            tipstersList = tipstersList.subList(0, 22);
            logger.info("Keep only the first 22 tipsters");
        }

        return tipstersList;
    }
}

//    @ExceptionHandler(StorageFileNotFoundException.class)
//    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
//        return ResponseEntity.notFound().build();
//    }

