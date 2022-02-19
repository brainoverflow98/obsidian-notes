package tr.com.havelsan.kovan.humanresources.engine.service.util;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PayrollCalculatorCache {

    private Map<Long, PayrollCalculator> cache = new HashMap<>();

    public PayrollCalculator get(Long payrollInfoId) {
        return this.cache.get(payrollInfoId);
    }

    public void add(Long payrollCalculationUuid, PayrollCalculator payrollCalculation) {
        this.cache.put(payrollCalculationUuid, payrollCalculation);
    }

    public void suspendAndRemove(Long payrollInfoId) {
        var payrollCalculator = get(payrollInfoId);
        if (payrollCalculator == null)
            return;
        Object lock = payrollCalculator.getLock();
        synchronized (lock) {
            payrollCalculator.getPayrollInfo().setIsCompleted(true);
            if (payrollCalculator.getNumberOfActiveCalculations() > 0) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            this.cache.remove(payrollInfoId);
        }

    }

}
