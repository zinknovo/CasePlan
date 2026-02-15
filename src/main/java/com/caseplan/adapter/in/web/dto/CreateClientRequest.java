package com.caseplan.adapter.in.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
public class CreateClientRequest {

    @NotBlank(message = "Client first name is required")
    private String firstName;

    @NotBlank(message = "Client last name is required")
    private String lastName;

    private String idNumber;
}
