package com.example.kkaribackend.domain.requirement.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class RequirementsNotFoundException extends KkariException {
    public RequirementsNotFoundException() {
        super(RequirementErrorCode.REQUIREMENTS_NOT_FOUND);
    }
}
