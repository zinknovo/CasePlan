package com.caseplan.adapter.in.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
public class CreateAttorneyRequest {

    @NotBlank(message = "Attorney name is required")
    private String name;

    @NotBlank(message = "Bar number is required")
    private String barNumber;
}
