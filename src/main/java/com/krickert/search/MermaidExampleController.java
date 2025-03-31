package com.krickert.search;

import io.micronaut.http.annotation.*;

@Controller("/mermaidExample")
public class MermaidExampleController {

    @Get(uri="/", produces="text/plain")
    public String index() {
        return "Example Response";
    }
}