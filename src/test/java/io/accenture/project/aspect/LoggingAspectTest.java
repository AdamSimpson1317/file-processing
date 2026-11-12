package io.accenture.project.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LoggingAspectTest {

    @Test
    void shouldProceedWithInterceptedMethod() throws Throwable {

        LoggingAspect loggingAspect = new LoggingAspect();

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        loggingAspect.logExecution(joinPoint);

        verify(joinPoint).proceed();
    }

    @Test
    void shouldPropagateExceptionFromInterceptedMethod() throws Throwable {

        LoggingAspect loggingAspect = new LoggingAspect();

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));

        assertThrows(
                RuntimeException.class,
                () -> loggingAspect.logExecution(joinPoint)
        );
    }
}