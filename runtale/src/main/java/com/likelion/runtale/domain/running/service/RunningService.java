package com.likelion.runtale.domain.running.service;

import com.likelion.runtale.common.exception.BadRequestException;
import com.likelion.runtale.common.exception.NotFoundException;
import com.likelion.runtale.common.response.ErrorMessage;
import com.likelion.runtale.domain.running.dto.RunningRequest;
import com.likelion.runtale.domain.running.dto.RunningResponse;
import com.likelion.runtale.domain.running.entity.Running;
import com.likelion.runtale.domain.running.entity.RunningStatus;
import com.likelion.runtale.domain.running.repository.RunningRepository;
import com.likelion.runtale.domain.user.entity.User;
import com.likelion.runtale.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RunningService {

    private final RunningRepository runningRepository;
    private final UserRepository userRepository;
    private static final int TTL_MINUTES = 15;

    public RunningResponse saveRunning(Long userId, RunningRequest runningRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessage.USER_NOT_EXIST));
        Running running;
        if (runningRequest.getId() != null) {
            running = runningRepository.findById(runningRequest.getId())
                    .orElseThrow(() -> new NotFoundException(ErrorMessage.RUNNING_NOT_FOUND));
            running.setEndTime(runningRequest.getEndTime());
            running.setDistance(runningRequest.getDistance());
            running.setPace(runningRequest.getPace());
            running.setStatus(runningRequest.getEndTime() == null ? RunningStatus.IN_PROGRESS : RunningStatus.COMPLETED);
        } else {
            running = runningRequest.toRunning();
            running.setUser(user);
        }
        running = runningRepository.save(running);
        running.setModifiedAt(LocalDateTime.now());
        user.addOrUpdateRunning(running);
        return new RunningResponse(running);
    }

    public List<Running> getRunningsByUserId(Long userId) {
        return runningRepository.findByUserIdWithUser(userId);
    }

    @Transactional(readOnly = true)
    public Running getRunningById(Long id) {
        return runningRepository.findById(id).orElseThrow(() -> new BadRequestException(ErrorMessage.RUNNING_NOT_FOUND));
    }

    public void deleteRunning(Long id) {
        Running running = runningRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(ErrorMessage.RUNNING_NOT_FOUND));

        User user = running.getUser();
        if (user != null) {
            // User의 runnings 리스트에서 Running 객체 제거
            user.getRunnings().remove(running);
        }
        runningRepository.delete(running);
    }

    // TTL이 지난 러닝 세션 삭제하는 스케줄러
    @Scheduled(fixedRate = 180  * 1000) // 3분마다 실행
    public void deleteExpiredRunningSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<Running> allRunnings = runningRepository.findAllWithUser();
        allRunnings.stream()
                .filter(running -> running.getStatus() == RunningStatus.IN_PROGRESS && running.getLastModifiedDate().plusMinutes(TTL_MINUTES).isBefore(now))
                .forEach(running -> {
                    User user = running.getUser();
                    if (user != null) {
                        user.getRunnings().remove(running); // User의 runnings 리스트에서 Running 객체 제거
                    }
                    runningRepository.delete(running);
                });
    }

}