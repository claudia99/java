package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import net.minidev.json.JSONUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.config.Task;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.print.attribute.standard.Media;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/tasks")
public class TaskController {
    @Autowired
    private TaskService service;

    @Operation(summary = "Search tasks", operationId = "getTasks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found tasks",
                    content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Object[].class))}
            ),
            @ApiResponse(responseCode = "204", description = "No tasks found")
    })
    @GetMapping
    public ResponseEntity<List<Object>> getTasks(@RequestParam(required = false) String title,
                                                 @RequestParam(required = false) String description,
                                                 @RequestParam(required = false) String assignedTo,
                                                 @RequestParam(required = false) TaskModel.TaskStatus status,
                                                 @RequestParam(required = false) TaskModel.TaskSeverity severity,
                                                 @RequestHeader(required = false, name = "X-Fields") String fields,
                                                 @RequestHeader(required = false, name = "X-Sort") String sort) {
        List<TaskModel> tasks = service.getTasks(title, description, assignedTo, status, severity);
        if (tasks.isEmpty()) {
            return ResponseEntity.noContent().build();
        } else {
            if (sort != null && !sort.isBlank()) {
                tasks = tasks.stream().sorted((first, second) -> BaseModel.sorter(sort).compare(first, second)).collect(Collectors.toList());
            }
            List<Object> items;
            if (fields != null && !fields.isBlank()) {
                items = tasks.stream().map(task -> task.sparseFields(fields.split(","))).collect(Collectors.toList());
            } else {
                items = new ArrayList<>(tasks);
            }
            return ResponseEntity.ok(items);
        }
    }


    @Operation(summary = "Get a task", operationId = "getTask")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found task",
                    content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Object.class))}
            ),
            @ApiResponse(responseCode = "404", description = "No task found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> getTaskById(@PathVariable String id, @RequestHeader(required = false, name = "X-Fields") String fields) {
        Optional<TaskModel> task = service.getTask(id);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else {
            if (fields != null && !fields.isBlank()) {
                return ResponseEntity.ok(task.get().sparseFields(fields.split(",")));
            } else {
                return ResponseEntity.ok(task.get());
            }
        }
    }

    @Operation(summary = "Create a task", operationId = "addTask")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task was created",
                    headers = {@Header(name = "location", schema = @Schema(type = "String"))}
            ),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "204", description = "Bulk tasks created")
    })
    @PostMapping
    public ResponseEntity<Void> addTask(@RequestBody String payload, @RequestHeader(required = false, name = "X-Action") String action) {
        try {
            if ("bulk".equals(action)) {
                for (TaskModel taskModel : new ObjectMapper().readValue(payload, TaskModel[].class)) {
                    service.addTask(taskModel);
                }
                return ResponseEntity.noContent().build();
            } else {
                TaskModel taskModel = service.addTask(new ObjectMapper().readValue(payload, TaskModel.class));
                URI uri = WebMvcLinkBuilder.linkTo(getClass()).slash(taskModel.getId()).toUri();
                return ResponseEntity.created(uri).build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update a task", operationId = "updateTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was updated"),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTask(@PathVariable String id, @RequestBody TaskModel task) {
        try {
            if (service.updateTask(id, task)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Patch a task", operationId = "patchTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was patched"),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patchTask(@PathVariable String id, @RequestBody TaskModel task) {
        try {
            if (service.patchTask(id, task)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete a task", operationId = "deleteTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was deleted"),
            @ApiResponse(responseCode = "500", description = "Something went wrong"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        try {
            if (service.deleteTask(id)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Check a task", operationId = "checkTask")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task was found"),
            @ApiResponse(responseCode = "404", description = "Task was not found")
    })
    @RequestMapping(method = RequestMethod.HEAD, value = "/{id}")
    public ResponseEntity checkTask(@PathVariable String id) {
        Optional<TaskModel> taskModel = service.getTask(id);
        return taskModel.isPresent() ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }


//export method

    @Operation(summary = "Search tasks", operationId = "getTasks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found tasks",
                    content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Object[].class))}
            ),
            @ApiResponse(responseCode = "204", description = "No tasks found")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, value = "/export/{format}")
    public ResponseEntity<List<Object>> exportTasks(@RequestParam(required = false) String title,
                                                    @RequestParam(required = false) String description,
                                                    @RequestParam(required = false) String assignedTo,
                                                    @RequestParam(required = false) TaskModel.TaskStatus status,
                                                    @RequestParam(required = false) TaskModel.TaskSeverity severity,
                                                    @RequestHeader(required = false, name = "X-Fields") String fields,
                                                    @RequestHeader(required = false, name = "X-Sort") String sort,
                                                    @PathVariable String format) throws IOException, ParserConfigurationException, TransformerException {

        String[] headers = {"severity", "description", "id", "title", "assignedTo", "status"};


        if (fields != null) {
            headers = fields.split(",");
        }

        DocumentBuilder builder = null;
        Document document = null;
        CSVPrinter csvPrinter = null;
        Element root = null;

        if (format.toLowerCase().equals("xml")) {
            try {
                builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw e;
            }
            document = builder.newDocument();
            root = document.createElement("tasks");
            document.appendChild(root);
        }
        else if (format.toLowerCase().equals("csv")) {
            try {
                BufferedWriter writer = Files.newBufferedWriter(Paths.get("./tasks.csv"));
                csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader(headers));
            } catch (Error e) {
                throw e;
            }
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }


        List<TaskModel> tasks = service.getTasks(title, description, assignedTo, status, severity);
        if (tasks.isEmpty()) {
            return ResponseEntity.noContent().build();
        } else {
            if (sort != null && !sort.isBlank()) {
                tasks = tasks.stream().sorted((first, second) -> BaseModel.sorter(sort).compare(first, second)).collect(Collectors.toList());
            }
            List<Object> items;
            if (fields != null && !fields.isBlank()) {
                items = tasks.stream().map(task -> task.sparseFields(fields.split(","))).collect(Collectors.toList());
            } else {
                String[] finalHeaders = headers;
                items = tasks.stream().map(task -> task.sparseFields(finalHeaders)).collect(Collectors.toList());
            }


            for (Object item : items) {
                if (format.toLowerCase().equals("csv")) {
                    List ceva = Arrays.asList(item.toString().replace("{", "").split("="));
                    String[] cevaa = ceva.toString().replace("}]", "").split(", ");
                    int current = 1;
                    int size = cevaa.length;
                    for (int i = 0; i < size; i++) {
                        cevaa[i] = cevaa[current];
                        size--;
                        current = current + 2;
                    }
                    csvPrinter.printRecord(Arrays.asList(cevaa).subList(0, size));
                    csvPrinter.flush();
                }

                if (format.toLowerCase().equals("xml")) {
                    List cevaxml = Arrays.asList(item.toString().replace("{", "").split(", "));
                    Element task = document.createElement("task");
                    root.appendChild(task);
                    for(Object it:cevaxml) {
                        String first, second;
                        List firstSecond = Arrays.asList(it.toString().replace("}","").split("="));
                        first = firstSecond.get(0).toString();
                        second = firstSecond.get(1).toString();
                        Element element = document.createElement(first);
                        element.setTextContent(second);
                        task.appendChild(element);
                    }
                    Transformer transformer;
                    try {
                        TransformerFactory transformerFactory = TransformerFactory
                                .newInstance();
                        transformer = transformerFactory.newTransformer();
                        Result output = new StreamResult(new File("tasks.xml"));
                        Source input = new DOMSource(document);
                        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                        transformer.transform(input, output);
                    } catch (TransformerConfigurationException e) {
                        throw e;
                    } catch (TransformerException e) {
                        throw e;}

                }

            }
            return ResponseEntity.ok(items);
        }

    }
}