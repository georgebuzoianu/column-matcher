package com.buzzo.tipsmatcher.exceltipsmatcher;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import cc.redberry.combinatorics.Combinatorics;
import com.buzzo.tipsmatcher.exceltipsmatcher.model.Tipster;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

//import hello.storage.StorageFileNotFoundException;
//import hello.storage.StorageService;

@Controller
@RequestMapping("/file")
public class FileUploadController {

//    private final StorageService storageService;
//
//    @Autowired
//    public FileUploadController(StorageService storageService) {
//        this.storageService = storageService;
//    }
//
//    @GetMapping("/")
//    public String listUploadedFiles(Model model) throws IOException {
//
//        model.addAttribute("files", storageService.loadAll().map(
//                path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class,
//                        "serveFile", path.getFileName().toString()).build().toString())
//                .collect(Collectors.toList()));
//
//        return "uploadForm";
//    }
//
//    @GetMapping("/files/{filename:.+}")
//    @ResponseBody
//    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
//
//        Resource file = storageService.loadAsResource(filename);
//        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
//                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
//    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("excelFile") MultipartFile file,
                                   @RequestParam("k") final Integer initK,
                                   RedirectAttributes redirectAttributes) throws IOException {

//        redirectAttributes.addFlashAttribute("message", "You successfully uploaded " + file.getOriginalFilename() + "!");

        List<Tipster> tipstersList = getTipstersFromFile(file);
        redirectAttributes.addFlashAttribute("tipsters" , tipstersList);


        final Integer k = initK > tipstersList.size() ? tipstersList.size() : initK;

        System.out.println(String.format("total found: %s and combine in sets of %s", tipstersList.size(), k));

        List<int[]> combinations = Combinatorics.combinations(tipstersList.size(), k).toList();



        List<List<Tipster>> allCombinations = combinations.stream().map(combination ->
        {
            List<Tipster> tipstersCombination = new ArrayList<>();
            for(int i=0; i < combination.length; i++)
                tipstersCombination.add(tipstersList.get(combination[i]));

            return tipstersCombination;
        }).collect(toList());

        List<Pair<Integer, List<Tipster>>> intermediary = allCombinations.stream().map(tipstersCombination -> {
            int noOfSuccesses = 0;
            for (int i = 1; i <= 31; i++) {
                final int day = i;
                int sum = tipstersCombination.stream().map(tipster -> tipster.getResults().get(day)).reduce(0, (a, b) -> a + b);
                if(sum == k )
                    noOfSuccesses ++;
            }
            return Pair.of(noOfSuccesses, tipstersCombination);
        })
        .filter(pair -> pair.getLeft()> 0)
        .collect(Collectors.toList());


        Map<Integer, List<List<Tipster>>> results = intermediary.stream().collect(groupingBy(Pair::getLeft, Collectors.mapping(Pair::getRight, Collectors.toList())));

        Map<Integer, List<List<Tipster>>> resultsSorted = results.entrySet().stream()
                                                        .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        redirectAttributes.addFlashAttribute("resultsSorted" , resultsSorted);

        System.out.println("tipsters found: " + tipstersList.size());
        return "redirect:/";
    }

    private List<Tipster> getTipstersFromFile(@RequestParam("excelFile") MultipartFile file) throws IOException {
        List<Tipster> tipstersList = new ArrayList<Tipster>();
        XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream());
        XSSFSheet worksheet = workbook.getSheetAt(0);

        for (int i = 1; i < worksheet.getPhysicalNumberOfRows(); i++) {


            XSSFRow row = worksheet.getRow(i);

            Tipster tipster = Tipster.builder()
                    .name(row.getCell(0).getStringCellValue())
                    .results(new HashMap())
                    .build();
            for (int day = 1; day <= 31; day++) {
                tipster.getResults().put(day, row.getCell(day)  !=null ? (int)row.getCell(day).getNumericCellValue() : 0);

            }
            tipstersList.add(tipster);
        }
        return tipstersList;
    }
}

//    @ExceptionHandler(StorageFileNotFoundException.class)
//    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
//        return ResponseEntity.notFound().build();
//    }

