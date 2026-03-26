package com.prismx.ai.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationFixIteration {
    private int iteration;
    private String command;
    private boolean compileSuccess;
    private List<String> filesChanged;
    private List<String> warnings;
    private List<String> errors;
    private String outputSnippet;

    public int iteration() { return iteration; }
    public String command() { return command; }
    public boolean compileSuccess() { return compileSuccess; }
    public List<String> filesChanged() { return filesChanged; }
    public List<String> warnings() { return warnings; }
    public List<String> errors() { return errors; }
    public String outputSnippet() { return outputSnippet; }
}
