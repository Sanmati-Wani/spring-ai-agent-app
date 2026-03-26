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
public class RequestBodyTemplateResponse {
    private String agentName;
    private CodeGenRequest requestBody;
    private List<String> notes;

    public String agentName() { return agentName; }
    public CodeGenRequest requestBody() { return requestBody; }
    public List<String> notes() { return notes; }
}
