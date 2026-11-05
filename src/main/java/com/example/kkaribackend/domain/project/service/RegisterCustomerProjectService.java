package com.example.kkaribackend.domain.project.service;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.project.presentation.dto.request.RegisterServiceRequest;
import com.example.kkaribackend.domain.project.presentation.dto.response.RegisterServiceResponse;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.domain.user.exception.EngineerNotFoundException;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RegisterCustomerProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final UserFacade userFacade;

    @Transactional
    public RegisterServiceResponse execute(RegisterServiceRequest request) {
        User customer = userFacade.getCurrentUser();
        User engineer = userRepository.findByIdAndRole(request.engineerId(), Role.MSP_ENGINEER)
            .orElseThrow(EngineerNotFoundException::new);

        Project project = projectRepository.save(
            Project.builder()
                .user(engineer)
                .customer(customer)
                .serviceName(request.serviceName())
                .answers(request.answers())
                .companyName(customer.getCompanyName())
                .contactName(customer.getName())
                .build()
        );
        return new RegisterServiceResponse(project.getId());
    }
}
