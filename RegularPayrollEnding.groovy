package tr.com.havelsan.kovan.humanresources.engine.service.core.main


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tr.com.havelsan.kovan.humanresources.engine.client.engine.model.calculator.CalculatorDataModel
import tr.com.havelsan.kovan.humanresources.engine.client.engine.model.PayrollInfoModel
import tr.com.havelsan.kovan.humanresources.engine.client.engine.model.PayrollSectionModel
import tr.com.havelsan.kovan.humanresources.engine.client.engine.model.PersonnelSnapshotModel
import tr.com.havelsan.kovan.humanresources.engine.client.script.model.ScriptModel
import tr.com.havelsan.kovan.humanresources.engine.service.engine.logic.*
import tr.com.havelsan.kovan.humanresources.engine.service.util.CoreFunctions
import tr.com.havelsan.kovan.humanresources.engine.service.util.PayrollCalculator
import tr.com.havelsan.kovan.humanresources.engine.service.util.PrepareScript
import tr.com.havelsan.kovan.humanresources.payroll.datalayer.entity.engine.PersonnelPayroll
import tr.com.havelsan.kovan.humanresources.payroll.datalayer.entity.engine.enums.PayrollTypeName

@Service
class RegularPayrollCalculator extends Script implements PayrollCalculator {

    private final PersonnelPayrollService personnelPayrollService;
    private final PersonnelSnapshotService personnelSnapshotService;
    private final PayrollSectionService payrollSectionService;
    private final ResultTableService resultTableService;
    private final CoreFunctions coreFunctions;

    @Autowired
    public RegularPayrollCalculator()
    {

    }

    private PayrollInfoModel payrollInfo;
    private Script parsedMainScript;
    private int numberOfActiveCalculations;
    private Object lock = new Object();


    @Override
    void init(PayrollInfoModel payrollInfo, List<ScriptModel> scriptModelList) {
        this.payrollInfo = payrollInfo;
        parseScripts(scriptModelList)
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void calculate(String hrmPerPeopleUuid) {
        PersonnelPayroll personnelPayroll = null;

        try {
            Long payrollInfoId = personnelPayrollService.checkIfCalculatedBefore(hrmPerPeopleUuid, this.payrollInfo.payrollTypeId, this.payrollInfo.calculatedYear, this.payrollInfo.calculatedPeriod);
            if (payrollInfoId == null) {
                List<PersonnelSnapshotModel> personnelSnapShotList = personnelSnapshotService.createSnapshot(hrmPerPeopleUuid);
                personnelPayroll = new PersonnelPayroll(this.payrollInfo.getId(), hrmPerPeopleUuid, false);
                personnelPayroll.personnelSnapshotId = personnelSnapShotList.last().id;
                personnelPayrollService.save(personnelPayroll);

                List<PayrollSectionModel> payrollSectionList = payrollSectionService.createSection(this.payrollInfo, personnelPayroll.id, personnelSnapShotList);
                var calculatorData = new CalculatorDataModel(this.payrollInfo, personnelPayroll);
                calculatorData.personnelSnapshot = personnelSnapShotList.last();
                coreFunctions.resetCalculatorData(calculatorData);
                this.calculatePersonnelPayroll(calculatorData, coreFunctions, payrollSectionList);
                resultTableService.saveAll(calculatorData.resultTableList);
                personnelPayroll.isSuccessful = true;
                personnelPayrollService.save(personnelPayroll);
            } else {
                personnelPayroll = new PersonnelPayroll(this.payrollInfo.getId(), hrmPerPeopleUuid, false);
                personnelPayroll.errorDesc = "Personnel payroll is already calculated in a previously executed payroll.";
                personnelPayrollService.save(personnelPayroll);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            if (personnelPayroll != null) {
                personnelPayroll.isSuccessful = false;
            } else {
                personnelPayroll = new PersonnelPayroll(getPayrollInfo().getId(), hrmPerPeopleUuid, false);
            }
            personnelPayroll.errorDesc = PrepareScript.handleException(e);
            personnelPayrollService.save(personnelPayroll);
        }
    }

    private void calculatePersonnelPayroll(CalculatorDataModel calculatorData, CoreFunctions coreFunctions, List<PayrollSectionModel> payrollSectionList) {
        this.parsedMainScript.invokeMethod("calculatePersonnelPayroll", new Object[]{calculatorData, coreFunctions, payrollSectionList});
    }

    @Override
    boolean tryStartCalculation() {
        synchronized (this.lock) {
            if (this.payrollInfo.isCompleted) {
                return false;
            }
            numberOfActiveCalculations++;
            return true;
        }
    }

    @Override
    void endCalculation() {
        synchronized (this.lock) {
            numberOfActiveCalculations--;
            if (numberOfActiveCalculations == 0) {
                this.lock.notifyAll();
            }
        }
    }

    @Override
    Object getLock() {
        return lock;
    }

    @Override
    PayrollTypeName getPayrollTypeName() {
        return PayrollTypeName.RegularPayroll
    }

    @Override
    PayrollInfoModel getPayrollInfo() {
        return this.payrollInfo;
    }

    @Override
    int getNumberOfActiveCalculations() {
        return this.numberOfActiveCalculations;
    }

    private void parseScripts(List<ScriptModel> scriptModelList) {
        def (
        ScriptModel            parameterScript,
        ScriptModel            sectionScript,
        ScriptModel            payrollEndingScript,
        ScriptModel            helperScript,
        ArrayList<ScriptModel> wageTypeScripts,
        ScriptModel            personnelParameterScript
        ) = PrepareScript.prepareScript(scriptModelList)

        var wageTypeScriptBuilder = new StringBuilder();
        for (scriptModel in wageTypeScripts) {
            wageTypeScriptBuilder.append(scriptModel.content).append(';')
        }

        String mainScript =
                '$$p = new Object(){' + (parameterScript.getContent() ?: "") + '};\n' +
                        'calculateSection = {$$, $$f, $$p, $$pp, $$h, $$wt ->' + sectionScript.getContent() + '};' +
                        'calculatePayrollEnding = {$$, $$f, $$p, $$pp, $$h, $$wt ->' + payrollEndingScript.getContent() + '};' +
                        'def calculatePersonnelPayroll(calculatorData, coreFunctions, payrollSectionList){' +
                        '    def $$ = calculatorData;' +
                        '    def $$f = coreFunctions;' +
                        '    def $$pp = new Object(){' + (personnelParameterScript.getContent() ?: "") + '};' +
                        '    def $$h = new Object(){' + (helperScript.getContent() ?: "") + '};' +
                        '    def $$wt = new Object(){' + wageTypeScriptBuilder.toString() + '};' +
                        '    $$f.calculate($$p, $$pp, $$h, $$wt, calculateSection, calculatePayrollEnding, payrollSectionList);' +
                        '}';
        $$.wageTypeFunctions.S1001_G2()
        $$.parameters.wageTypeGeneralList
        var p = 8
        var calculatorData = new CalculatorDataModel(this.payrollInfo);
        var coreFunctions = new CoreFunctions(calculatorData, this.generatorService, this.payrollLogger);
        var binding = new Binding();
        binding.setProperty('$$', calculatorData);
        binding.setProperty('$$f', coreFunctions);
        def shell = new GroovyShell(binding);
        this.parsedMainScript = shell.parse(mainScript)
        this.parsedMainScript.run();
    }

    @Override
    Object run() {
        return this
    }

}
