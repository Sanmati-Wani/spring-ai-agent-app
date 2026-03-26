package com.prismx.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointDetails {
    @Schema(description = "API URL/path details")
    private Object url;

    @Schema(description = "Request details")
    private Object request;

    @Schema(description = "Response details")
    private Object response;
}
