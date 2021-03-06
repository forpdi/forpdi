package org.forrisco.core.unit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.forpdi.core.abstractions.AbstractController;
import org.forpdi.core.company.CompanyDomain;
import org.forpdi.core.event.Current;
import org.forpdi.core.user.User;
import org.forpdi.core.user.authz.AccessLevels;
import org.forpdi.core.user.authz.Permissioned;
import org.forpdi.system.PDFgenerate;
import org.forrisco.core.plan.PlanRisk;
import org.forrisco.risk.Contingency;
import org.forrisco.risk.Incident;
import org.forrisco.risk.Monitor;
import org.forrisco.risk.PreventiveAction;
import org.forrisco.risk.Risk;
import org.forrisco.risk.RiskBS;
import org.forrisco.risk.objective.RiskActivity;
import org.forrisco.risk.objective.RiskProcess;
import org.forrisco.risk.objective.RiskStrategy;
import org.forrisco.core.process.Process;
import org.forrisco.core.process.ProcessBS;
import org.forrisco.core.process.ProcessUnit;
import org.forrisco.core.unit.permissions.EditUnitPermission;
import org.forrisco.core.unit.permissions.ManageUnitPermission;

import com.itextpdf.text.DocumentException;

import br.com.caelum.vraptor.Consumes;
import br.com.caelum.vraptor.Controller;
import br.com.caelum.vraptor.Delete;
import br.com.caelum.vraptor.Get;
import br.com.caelum.vraptor.Post;
import br.com.caelum.vraptor.Put;
import br.com.caelum.vraptor.boilerplate.NoCache;
import br.com.caelum.vraptor.boilerplate.bean.PaginatedList;
import br.com.caelum.vraptor.boilerplate.util.GeneralUtils;

/**
 * @author Matheus Nascimento
 */
@Controller
public class UnitController extends AbstractController {

	@Inject
	private UnitBS unitBS;
	@Inject
	private RiskBS riskBS;
	@Inject 
	private ProcessBS processBS;
	@Inject
	private PDFgenerate pdf;
	@Inject 
	@Current 
	private CompanyDomain domain;
	
	private static Map<Long, Long> duplicatesUnitsId= new HashMap <Long, Long>();

	protected static final String PATH = BASEPATH + "/unit";

	/**
	 * Salvar unidade
	 * 
	 * @param Unit
	 *            unidade a ser salva
	 */
	@Post(PATH + "/new")
	@Consumes
	@NoCache
	@Permissioned(value = AccessLevels.COMPANY_ADMIN, permissions = {ManageUnitPermission.class})
	public void save(@NotNull @Valid Unit unit) {
		try {
			PlanRisk planRisk = this.unitBS.exists(unit.getPlanRisk().getId(), PlanRisk.class);
			if (planRisk == null) {
				this.fail("Unidade não possui Plano de Risco");
			}
			unit.setId(null);
			unit.setPlanRisk(planRisk);
			this.unitBS.save(unit);
			this.success(unit);
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}

	/**
	 * Salvar subunidade
	 * 
	 * @param Unit
	 *            subunidade a ser salva
	 */
	@Post(PATH + "/subnew")
	@Consumes
	@NoCache
	@Permissioned(value = AccessLevels.COMPANY_ADMIN, permissions = {ManageUnitPermission.class})
	public void saveSub(@NotNull @Valid Unit unit) {
		try {

			if (unit.getPlanRisk() == null) {
				this.fail("Unidade não possui Plano de Risco");
				return;
			}

			if (unit.getParent() == null) {
				this.fail("Unidade não possui unidade pai");
				return;
			}

			unit.setId(null);
			this.unitBS.save(unit);
			this.success(unit);
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}
	
	
	/**
	 * Duplica unidades
	 *  
	 *  @Param List<Unit> lista de unidade com o id da unidade ogirinal
	 *  				 e o id no plano a ser salvo a unidade duplicado
	 * @return void
	 */
	@Post(PATH + "/duplicate")
	@Consumes
	@NoCache
	@Permissioned(value = AccessLevels.COMPANY_ADMIN, permissions = {ManageUnitPermission.class})
	public void duplicateUnit(@NotNull @Valid List<Unit> units, PlanRisk planRisk) {
		try {
		
			PlanRisk plan = this.unitBS.exists(planRisk.getId(), PlanRisk.class);
			if (plan == null || plan.isDeleted()) {
				this.fail("Plano de Risco não encontrado");
			}
			
			List<Unit> allunits = new ArrayList<>();
			
			//duplicar unidades/subunidades
			for(Unit unit: units) {
				
				unit = this.unitBS.exists(unit.getId(), Unit.class);
				if (unit == null || unit.isDeleted()) {
					//this.fail("Unidade não possui Plano de Risco");
					continue;
				}
	
				Unit newUnit= new Unit(unit);
				newUnit.setPlanRisk(plan);
				this.unitBS.save(newUnit);
				duplicatesUnitsId.put(unit.getId(), newUnit.getId());
				allunits.add(newUnit);
				
				PaginatedList<Unit> subunits = this.unitBS.listSubunitByUnit(unit);	
				
				for(Unit subunit: subunits.getList()) {
					Unit newSubunit= new Unit(subunit);
					newSubunit.setParent(newUnit);
					newSubunit.setPlanRisk(plan);
					this.unitBS.save(newSubunit);
					duplicatesUnitsId.put(subunit.getId(), newSubunit.getId());
					allunits.add(newSubunit);
				}
			}
			
			for(Unit unit: allunits) {
				duplicateProcess(unit);
				duplicateRisk(unit);
			}
			
			this.success("successfully duplicated plan risk");
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}

	private void duplicateProcess(Unit unit) {
		
		unit = this.unitBS.exists(unit.getId(), Unit.class);
		if (unit == null || unit.isDeleted()) {
			return;
		}
		
		Unit original = null;
		for(Entry<Long, Long> entry : duplicatesUnitsId.entrySet()) {
		    Long key = entry.getKey();
		    Long value = entry.getValue();

		    if(value==unit.getId()) {
		    	original = this.unitBS.exists(key, Unit.class);
		    	break;
		    }
		}
		
		if(original!=null) {

			PaginatedList<Process> processes= this.processBS.listProcessByUnit(original);
	
			for(Process process : processes.getList()) {
	
				ProcessUnit processUnit = new ProcessUnit();
				Unit mapedUnit = this.unitBS.exists(duplicatesUnitsId.get(original.getId()), Unit.class);
				
				processUnit.setUnit(mapedUnit);
				processUnit.setProcess(process);
				process.setRelatedUnits(null);
				this.processBS.save(processUnit);
			}

		}
		
	}

	private void duplicateRisk(Unit unit){
		
		PaginatedList<Risk> risks= new PaginatedList<>();
		
		for(Entry<Long, Long> entry : duplicatesUnitsId.entrySet()) {
		    Long key = entry.getKey();
		    Long value = entry.getValue();

		    if(value==unit.getId()) {
		    	
		    	Unit original = this.unitBS.exists(key, Unit.class);
				if (original == null || original.isDeleted()) {
					continue;
				}
					risks = this.riskBS.listRiskByUnit(original);
		    	break;
		    }
		}
		
		
		for(Risk risk : risks.getList()) {
			PaginatedList<PreventiveAction> actions =this.riskBS.listPreventiveActionByRisk(risk);
			
			PaginatedList<RiskStrategy> strategies = this.riskBS.listRiskStrategy(risk);
			PaginatedList<RiskActivity> activities = this.riskBS.listRiskActivity(risk);
			PaginatedList<RiskProcess> processes = this.riskBS.listRiskProcess(risk);
			
			PaginatedList<Monitor> monitors = this.riskBS.listMonitorByRisk(risk);
			PaginatedList<Incident> incidents = this.riskBS.listIncidentsByRisk(risks);
			PaginatedList<Contingency> contingencies = this.riskBS.listContingenciesByRisk(risk);
			
			Risk newRisk = new Risk(risk);
			newRisk.setUnit(unit);
			this.riskBS.saveRisk(newRisk);
			
			
			for( PreventiveAction action : actions.getList()) {
				PreventiveAction act= new PreventiveAction(action);
				act.setRisk(newRisk);
				this.riskBS.saveAction(act);
			}
			
			for(Monitor monitor : monitors.getList()) {
				monitor= new Monitor(monitor);
				monitor.setRisk(newRisk);
				this.riskBS.saveMonitor(monitor);
				//nao esta salvando no banco
			}
			
			for(Incident incident : incidents.getList()) {
				Incident icd= new Incident(incident);
				icd.setRisk(newRisk);
				this.riskBS.saveIncident(icd);
			}
			
			for(Contingency contingency : contingencies.getList()) {
				Contingency cont= new Contingency(contingency);
				cont.setRisk(newRisk);
				this.riskBS.saveContingency(cont);
			}
			

			if(strategies !=null) {
				for(RiskStrategy strategy: strategies.getList()) {
					RiskStrategy str= new RiskStrategy();
					str.setLinkFPDI(strategy.getLinkFPDI());
					str.setName(strategy.getName());
					str.setRisk(newRisk);
					str.setStructure(strategy.getStructure());
					this.riskBS.persist(str);
				}
			}
			
			if(activities !=null) {
				for(RiskActivity activity: activities.getList()) {
					RiskActivity act = new RiskActivity(activity);
						act.setRisk(newRisk);
						act.setProcess(activity.getProcess());
						this.riskBS.persist(act);
				}
			}
			
			if(processes !=null) {
				for(RiskProcess process: processes.getList()) {
					RiskProcess riskpro = new RiskProcess(process);
					riskpro.setRisk(newRisk);
					riskpro.setProcess(process.getProcess());
					this.riskBS.persist(riskpro);
				}
			}
		}
	}
	
	
	

	/**
	 * Retorna unidades.
	 * 
	 * @param PLanRisk
	 *            Id do plano de risco.
	 * @return <PaginatedList> Unit
	 */
	@Get(PATH + "")
	@NoCache
	@Consumes
	@Permissioned
	public void listUnits(@NotNull Long planId) {
		try {
			PlanRisk plan = this.unitBS.exists(planId, PlanRisk.class);
			
			if (plan== null) {
				this.fail("O Plano de Risco não foi encontrado");
				return;
			}
			
			PaginatedList<Unit> units = this.unitBS.listOnlyUnitsbyPlanRisk(plan);
			
			//PaginatedList<Unit> units =this.unitBS.listUnitsbyPlanRisk(plan);
			this.success(units);
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}

	/**
	 * Retorna unidade.
	 * 
	 * @param id
	 *            Id da unidade a ser retornado.
	 * @return Unit Retorna a unidade de acordo com o id passado.
	 */
	@Get(PATH + "/{id}")
	@NoCache
	@Permissioned
	public void getUnit(Long id) {
		try {
			Unit unit = this.unitBS.retrieveUnitById(id);
			if (unit == null) {
				this.fail("A unidade solicitada não foi encontrado.");
			} else {
				this.success(unit);
			}
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}
	
	/**
	 * Retorna subunidades.
	 * 
	 * @param unitId
	 *            Id da unidade parent.
	 * @return <PaginatedList> Subunidades filhas da unidade passada
	 */
	@Get(PATH + "/listsub/{unitId}")
	@NoCache
	@Consumes
	@Permissioned
	public void listSubunits(@NotNull Long unitId) {
		try {
			Unit unit = this.unitBS.exists(unitId, Unit.class);
			
			if (unit == null) {
				this.fail("A unidade não foi encontrada");
				return;
			}
			
			PaginatedList<Unit> subunits = this.unitBS.listSubunitByUnit(unit);
			this.success(subunits);
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}
	
	
	/**
	 * Retorna todas subunidades do plano.
	 * 
	 * @param planId
	 *            Id do plano de risco
	 * @return <PaginatedList> Subunidades
	 */
	@Get(PATH + "/listsub")
	@NoCache
	@Consumes
	@Permissioned
	public void listSubunitsByPlan(@NotNull Long planId) {
		try {
			PlanRisk plan =this.unitBS.exists(planId, PlanRisk.class);
			
			if (plan == null || plan.isDeleted()) {
				this.fail("O Plano de risco não foi encontrado");
				return;
			}
			
			PaginatedList<Unit> units = this.unitBS.listUnitsbyPlanRisk(plan);
			List<Unit> list = new ArrayList<>();
			
			for(Unit unit : units.getList()) {
				PaginatedList<Unit> subunits = this.unitBS.listSubunitByUnit(unit);
				list.addAll(subunits.getList());
			}
			
			PaginatedList<Unit> result = new PaginatedList<Unit>();
			
			result.setList(list);
			result.setTotal((long) list.size());
			this.success(result);
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}
	
	/**
	 * Retorna todas unidades e subunidades do plano.
	 * 
	 * @param planId
	 *            Id do plano de risco
	 * @return <PaginatedList>Unit
	 */
	@Get(PATH + "/allByPlan")
	@NoCache
	@Consumes
	@Permissioned
	public void listAllUnitsByPlan(@NotNull Long planId) {
		try {
			PlanRisk plan =this.unitBS.exists(planId, PlanRisk.class);
			
			if (plan == null || plan.isDeleted()) {
				this.fail("O Plano de risco não foi encontrado");
				return;
			}
			
			PaginatedList<Unit> units = this.unitBS.listUnitsbyPlanRisk(plan);
			
			this.success(units);
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}
	
	

	/**
	 * Exclui unidade.
	 * 
	 * @param id
	 *            Id da unidade a ser excluído.
	 *
	 */
	@Delete(PATH + "/{id}")
	@NoCache
	@Permissioned(value = AccessLevels.COMPANY_ADMIN, permissions = {ManageUnitPermission.class})
	public void deleteUnit(@NotNull Long id) {
		try {

			Unit unit = this.unitBS.exists(id, Unit.class);
			if (GeneralUtils.isInvalid(unit)) {
				this.result.notFound();
				return;
			}
			
			if (this.unitBS.deletableUnit(unit)) {
				//deletar processos desta unidade
				PaginatedList<Process> processes = this.processBS.listProcessByUnit(unit);
				for(Process process :processes.getList()) {
					this.processBS.deleteProcess(process);
				}
				this.unitBS.delete(unit);
				this.success(unit);
			} else {
				this.fail("A unidade possuiu riscos ou subunidades, portanto não pode ser excluida." );
				return;
			}
			
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Ocorreu um erro inesperado: " + ex.getMessage());
		}
	}



	/**
	 * Atualiza unidade.
	 * 
	 * @param unit
	 *            Unidade a ser atualizada.
	 *
	 */
	@Put(PATH)
	@NoCache
	@Consumes
	@Permissioned(value = AccessLevels.MANAGER, permissions = {ManageUnitPermission.class, EditUnitPermission.class})
	public void updateUnit(@NotNull Unit unit) {
		try {
			Unit existent = this.unitBS.exists(unit.getId(), Unit.class);
			if (existent == null) {
				this.fail("A unidade não foi encontrada.");
				return;
			}
			
			User user = this.riskBS.exists(unit.getUser().getId(), User.class);
			if (user == null) {
				this.fail("O usuário responsável não foi encontrado.");
				return;
			}

			existent.setName(unit.getName());
			existent.setAbbreviation(unit.getAbbreviation());
			existent.setUser(user);
			existent.setDescription(unit.getDescription());
			
			this.unitBS.persist(existent);
			
			this.success(existent);
		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Ocorreu um erro inesperado: " + ex.getMessage());
		}
	}
	
	
	
	
	
	/**
	 * Listar Unidades e seus níveis segundo uma chave de busca.
	 * 
	 * @param parentId
	 *            Id do Plano de risco.
	 * @param page
	 *            Número da página da lista de plano de metas.
	 * @param terms
	 *            Termo de busca.
	 * @param itensSelect
	 *            Conjunto de unidades a serem buscadas.
	 * @param subitensSelect
	 *            Conjunto de subunidades que podem ser buscados.
	 * @param ordResult
	 *            Ordenação do resultado, 1 para crescente e 2 para decrescente.
	 *            
	 * @return PaginatedList<Plan> Retorna lista de planos de metas de acordo
	 *         com os filtros.
	 */
	
	@Get(PATH + "/searchByKey")
	@NoCache
	@Permissioned
	public void listItensTerms(Long planRiskId, Integer page, String terms, Long itensSelect[], Long subitensSelect[], int ordResult, Long limit) {
		if (page == null)
			page = 0;
		
		try {
	
			PlanRisk planRisk = this.unitBS.exists(planRiskId, PlanRisk.class);
			
			if(planRisk.isDeleted()) {
				this.fail("plano não foi encontrado");
			}
			
			Long[] itens=  (Long[]) ArrayUtils.addAll(itensSelect, subitensSelect); ;
		
			List<Unit> units = this.unitBS.listUnitTerms(planRisk, terms, itens, ordResult);
			PaginatedList<Unit> result = TermResult(units, page, limit);
			
			this.success(result);
 		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}
	
	@Get(PATH + "/search")
	@NoCache
	@Permissioned
	public void listItensTerms(Long planRiskId, Integer page, String terms, int ordResult, Long limit) {
		if (page == null) page = 0;
		
		try {
			PlanRisk planRisk = this.unitBS.exists(planRiskId, PlanRisk.class);
			
			if(planRisk.isDeleted()) {
				this.fail("plano não foi encontrado");
			}
			
			List<Unit> units = this.unitBS.listUnitTerms(planRisk, terms, null, ordResult);
			PaginatedList<Unit> result = TermResult(units, page, limit);
			
			this.success(result);
 		} catch (Throwable ex) {
			LOGGER.error("Unexpected runtime error", ex);
			this.fail("Erro inesperado: " + ex.getMessage());
		}
	}
	
	private PaginatedList<Unit> TermResult(List<Unit> units, Integer page,  Long limit){
		int firstResult = 0;
		int maxResult = 0;
		int count = 0;
		int add = 0;
		if (limit != null) {
			firstResult = (int) ((page - 1) * limit);
			maxResult = limit.intValue();
		}
		
		List<Unit> list = new ArrayList<>();
		for(Unit unit : units) {
			if (limit != null) {
				if (count >= firstResult && add < maxResult) {
					list.add(unit);
					count++;
					add++;
				} else {
					count++;
				}
			} else {
				list.add(unit);
			}
		}

		PaginatedList<Unit> result = new PaginatedList<Unit>();
		
		result.setList(list);
		result.setTotal((long)count);
		return result;
	}
	
	
	
	/**
	 * Cria arquivo pdf  para exportar relatório  
	 * 
	 * 
	 * @param title
	 * @param author
	 * @param pre
	 * @param item
	 * @param subitem
	 * @throws DocumentException 
	 * @throws IOException 
	 * 
	 */
	@Get(PATH + "/exportUnitReport")
	@NoCache
	@Permissioned
	public void exportreport(String title, String author, boolean pre, String units,String subunits){
		try {
		
			//adicionar os arquivos anexos aos processos?
			File pdf = this.pdf.exportUnitReport(title, author, units, subunits);

			OutputStream out;
			FileInputStream fis= new FileInputStream(pdf);
			this.response.reset();
			this.response.setHeader("Content-Type", "application/pdf");
			this.response.setHeader("Content-Disposition", "inline; filename=\"" + title + ".pdf\"");
			out = this.response.getOutputStream();
			
			IOUtils.copy(fis, out);
			out.close();
			fis.close();
			pdf.delete();
			this.result.nothing();
			
		} catch (Throwable ex) {
			LOGGER.error("Error while proxying the file upload.", ex);
			this.fail(ex.getMessage());
		}
	}
}
