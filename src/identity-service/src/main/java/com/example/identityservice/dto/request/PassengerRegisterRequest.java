package com.example.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@Schema(description = "Request payload to register a passenger account")
public class PassengerRegisterRequest extends AccountRegisterRequest {
}
