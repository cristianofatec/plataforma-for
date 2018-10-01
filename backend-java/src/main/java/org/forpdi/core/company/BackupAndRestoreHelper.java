package org.forpdi.core.company;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.forpdi.core.event.Current;
import org.forpdi.core.user.User;
import org.forpdi.core.user.UserBS;
import org.forpdi.dashboard.manager.LevelInstanceHistory;
import org.forpdi.planning.attribute.AggregateIndicator;
import org.forpdi.planning.attribute.Attribute;
import org.forpdi.planning.attribute.AttributeInstance;
import org.forpdi.planning.document.Document;
import org.forpdi.planning.document.DocumentAttribute;
import org.forpdi.planning.document.DocumentBS;
import org.forpdi.planning.document.DocumentSection;
import org.forpdi.planning.fields.actionplan.ActionPlan;
import org.forpdi.planning.fields.budget.Budget;
import org.forpdi.planning.fields.budget.BudgetBS;
import org.forpdi.planning.fields.budget.BudgetElement;
import org.forpdi.planning.plan.Plan;
import org.forpdi.planning.plan.PlanBS;
import org.forpdi.planning.plan.PlanDetailed;
import org.forpdi.planning.plan.PlanMacro;
import org.forpdi.planning.structure.Structure;
import org.forpdi.planning.structure.StructureBS;
import org.forpdi.planning.structure.StructureLevel;
import org.forpdi.planning.structure.StructureLevelInstance;
import org.forpdi.planning.structure.StructureLevelInstanceDetailed;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;

import br.com.caelum.vraptor.boilerplate.HibernateBusiness;
import br.com.caelum.vraptor.boilerplate.SimpleLogicalDeletableEntity;
import br.com.caelum.vraptor.boilerplate.bean.PaginatedList;
import br.com.caelum.vraptor.observer.upload.UploadedFile;
import br.com.caelum.vraptor.serialization.gson.GsonSerializerBuilder;

@RequestScoped
public class BackupAndRestoreHelper extends HibernateBusiness {

	private Gson gson;

	@Inject private PlanBS planBS;
	@Inject private DocumentBS docBS;
	@Inject private StructureBS structureBS;
	@Inject private BudgetBS budgetBS;
	@Inject private CompanyBS companyBS;
	@Inject private UserBS userBS;
	@Inject @Current private CompanyDomain domain;
	
	@Inject
	public BackupAndRestoreHelper(GsonSerializerBuilder gsonBuilder) {
		gsonBuilder.setWithoutRoot(true);
		gsonBuilder.indented();
		this.gson = gsonBuilder.create();
	}

	@Deprecated
	protected BackupAndRestoreHelper() {}

	/**
	 * consulta o banco e salva as informações em um arquivo
	 *
	 * @throws IOException
	 *
	 */
	public byte[] export(Company company) throws IOException {
		if (company == null) {
			throw new IllegalArgumentException("Company not found.");
		}
		
		PaginatedList<PlanMacro> plansMacroList = this.planBS.listAllMacros(company);
		
		List<ActionPlan>  actionplan = new ArrayList<>();
		List<LevelInstanceHistory>  levelinstancehistory = new ArrayList<>();
		List<StructureLevelInstanceDetailed>  slid = new ArrayList<>();	
		List<AttributeInstance> attributeinstance = new ArrayList<>();	
		List<Budget> budget =   new ArrayList<>();
		List<DocumentAttribute> documentattribute = new ArrayList<>();	
		List<AggregateIndicator> aggregateindicator = new ArrayList<>();
		
		List<PlanMacro> plansMacro =new ArrayList<>();
		List<Document> documents = new ArrayList<>();
		List<Plan> plans= new ArrayList<>();
		List<PlanDetailed> plandetailed= new ArrayList<>();
		List<Structure> structures = new ArrayList<>();
		List<StructureLevel> structurelevel = new ArrayList<>();
		List<StructureLevelInstance> structurelevelinstance = new ArrayList<>();
		List<DocumentSection> documentsection = new ArrayList<>();
		List<BudgetElement> budgetelement = new ArrayList<>();
		List<Attribute> attribute = new ArrayList<>();
		
		HashMap<Long, Structure> structuresMap = new HashMap<>();
		HashMap<Long, StructureLevel> structuresLevelMap = new HashMap<>();
		HashMap<Long, BudgetElement> budgetElementMap = new HashMap<>();
		HashMap<Long, Attribute> attributeMap = new HashMap<>();

	
		for(PlanMacro planMacro : plansMacroList.getList()) {
				
			//Exportando o Plano Macro
			planMacro.setExportCompanyId(planMacro.getCompany().getId());
			planMacro.setCompany(null);
			
			plansMacro.add(planMacro);

			List<Plan> plan = this.planBS.listAllPlansForPlanMacro(planMacro);
			
			//Exportando os planos de metas
			for (Plan plan_pm : plan) {
				Structure structure = plan_pm.getStructure();
				plan_pm.setExportPlanMacroId(planMacro.getId());
				plan_pm.setExportStructureId(structure.getId());
				plan_pm.setParent(null);
				plan_pm.setStructure(null);
				plans.add(plan_pm);
				if (!structuresMap.containsKey(structure.getId())) {
					structuresMap.put(structure.getId(), structure);
				}
		
				//Exporta plano detalhado
				List<PlanDetailed> plansdetailed = this.planBS.listAllPlanDetailed(plan_pm);
				for (PlanDetailed pd : plansdetailed) {
					pd.setPlan(null);
					pd.setExportPlanId(plan_pm.getId());
					plandetailed.add(pd);
				}
				
				//Exportando as estruturas level instance necessárias.
				List<StructureLevelInstance> structurelevelsinstance = this.structureBS.listAllLevelInstanceByPlan(plan_pm);
				for (StructureLevelInstance sli: structurelevelsinstance) {
					
					sli.setExportLevelId(sli.getLevel().getId());
					sli.setExportPlanId(plan_pm.getId());
					
					if (!structuresLevelMap.containsKey(sli.getLevel().getId())) {
						structuresLevelMap.put(sli.getLevel().getId(), sli.getLevel());
					}
					
					sli.setPlan(null);
					sli.setLevel(null);
					
					List<AttributeInstance> x = this.structureBS.listAttributeInstanceByLevel(sli);
					
					x.stream().forEach(it ->{
						if( it.getAttribute().getLabel().equals("Responsável")) {
							User user = this.userBS.existsByUser( Long.parseLong(it.getValue()));
							it.getAttribute();
							sli.setExportResponsibleMail(user.getEmail());
						}
					});					
					
					structurelevelinstance.add(sli);
				}
			}	
		}
			
		//Exportando as estruturas level necessárias.
		structuresLevelMap.values().stream().forEach(structureLevel -> {
			structureLevel.setExportStructureId(structureLevel.getStructure().getId());
			if (!structuresMap.containsKey(structureLevel.getStructure().getId())) {
				structuresMap.put(structureLevel.getStructure().getId(), structureLevel.getStructure());
			}
			structureLevel.setStructure(null);
			structurelevel.add(structureLevel);		
			
			//Exportando atributos
			for(Attribute sl : structureBS.listAttributes(structureLevel).getList()) {
				attributeMap.put(sl.getId(),sl);
			}
		});

		//Exportando as estruturas necessárias.
		structuresMap.values().stream().forEach(structure -> {
			structure.setExportCompanyId(structure.getCompany().getId());
			structure.setCompany(null);
			structures.add(structure);		
		});
			
		
		for(PlanMacro pm: plansMacro ) {
			
			//Exportando os documentos
			Document doc =  this.docBS.retrieveDocumentByPlan(pm);	

			if(doc !=null){
				
				doc.setExportPlanMacroId(pm.getId());
				doc.setPlan(null);
				documents.add(doc);
		
				//Exportando as seções documentos
				List<DocumentSection> documentsection_temp = this.docBS.listAllSectionsByDocument(doc);
				HashMap<Long, DocumentSection> documentsectionMap =   new HashMap<>();
				boolean possui_parent;
				
				do{
					List<DocumentSection>documentsection_temp_2 = new ArrayList<>();
					
					documentsection_temp.stream().forEach( it->{
						documentsectionMap.put(it.getId(), it);
						if(it.getParent() !=null) {
							documentsection_temp_2.add(it.getParent());
						}
					});
		
					documentsection_temp = new ArrayList<>();
					documentsection_temp.addAll(documentsection_temp_2);
					
					possui_parent=false;
					if(!documentsection_temp.isEmpty()) {
						possui_parent=true;
					}
					
				}while(possui_parent);
				
				documentsectionMap.values().stream().forEach(ds -> {
					if(ds.getParent()!=null){
						ds.setExportDocumentSectionId(ds.getParent().getId());
					}
					ds.setExportDocumentId(ds.getDocument().getId());
					ds.setDocument(null);
					ds.setParent(null);
					ds.setDocumentAttributes(null);
					documentsection.add(ds);
									
				});				
			}
		}
		
		//Exportando document attribute
		documentsection.stream().forEach(ds->{
			List<DocumentAttribute> list = this.docBS.listAllAttributesBySection(ds);
			list.stream().forEach(it->{
				it.setExportDocumentSectionId(it.getSection().getId());
				it.setSection(null);
				documentattribute.add(it);
			});

		});
			
		//Exportando planos de ação
		//Exportando level instance history
		//Exportando structure level instance detailed
		//Exportando aggregate indicator
		//Exportando budget
		structurelevelinstance.stream().forEach( 
				sli->{
			actionplan.addAll((List<ActionPlan>) this.dao.newCriteria(ActionPlan.class).add(Restrictions.eq("levelInstance", sli)).list());
			levelinstancehistory.addAll((List<LevelInstanceHistory>) this.dao.newCriteria(LevelInstanceHistory.class).add(Restrictions.eq("levelInstance", sli)).list());
			slid.addAll((List<StructureLevelInstanceDetailed>) structureBS.listAllLevelInstanceDetailed(sli));	
			budget.addAll((List<Budget>) budgetBS.listAllBudgetByLevelInstance(sli));
			attributeinstance.addAll((List<AttributeInstance>) this.dao.newCriteria(AttributeInstance.class).add(Restrictions.eq("levelInstance", sli)).list());
			aggregateindicator.addAll((List<AggregateIndicator>) this.dao.newCriteria(AggregateIndicator.class).add(Restrictions.eq("aggregate", sli)).list());
		});

		actionplan.stream().forEach(it->{
			it.setExportStructureLevelInstanceId(it.getLevelInstance().getId());
			it.setLevelInstance(null);	
		});
		
		levelinstancehistory.stream().forEach(it->{
			it.setExportStructureLevelInstanceId(it.getLevelInstance().getId());
			it.setLevelInstance(null);	
		});
		
		slid.stream().forEach(it->{
			it.setExportStructureLevelInstanceId(it.getLevelInstance().getId());
			it.setLevelInstance(null);	
		});
		
		aggregateindicator.stream().forEach(it->{
			it.setExportAggregateId(it.getAggregate().getId());
			it.setExportIndicatorId(it.getIndicator().getId());
			it.setAggregate(null);
			it.setIndicator(null);
		});
		
		budget.stream().forEach(it->{
			budgetElementMap.put(it.getBudgetElement().getId(),it.getBudgetElement());
			it.setExportStructureLevelInstanceId(it.getLevelInstance().getId());
			it.setExportBudgetElementId(it.getBudgetElement().getId());
			it.setLevelInstance(null);	
			it.setBudgetElement(null);
		});
		
		//Exportando budget element
		budgetElementMap.values().stream().forEach(be -> {
			be.setExportCompanyId(be.getCompany().getId());
			be.setCompany(null);
			budgetelement.add(be);
		});
		
		//Exportando atributos
		attributeinstance.stream().forEach(it->{
			attributeMap.put(it.getAttribute().getId(),it.getAttribute());
			it.setExportStructureLevelInstanceId(it.getLevelInstance().getId());
			it.setExportAttributeId(it.getAttribute().getId());
			it.setLevelInstance(null);	
			it.setAttribute(null);
		});
	
		attributeMap.values().stream().forEach(a -> {
			a.setExportStructureLevelId(a.getLevel().getId());
			a.setLevel(null);
			attribute.add(a);
		});
		
			
			
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(bos);	
		
		zipAdd(zos, Company.class.getSimpleName(), this.gson.toJson(company));
		if(!plansMacro.isEmpty())
		 zipAdd(zos, PlanMacro.class.getSimpleName(), this.gson.toJson(plansMacro));
		if(!budgetelement.isEmpty())
			zipAdd(zos, BudgetElement.class.getSimpleName(), this.gson.toJson(budgetelement));
		if(!structures.isEmpty())
			zipAdd(zos, Structure.class.getSimpleName(), this.gson.toJson(structures));
		
		if(!documents.isEmpty())
			zipAdd(zos, Document.class.getSimpleName(), this.gson.toJson(documents));
		if(!structurelevel.isEmpty())
			zipAdd(zos, StructureLevel.class.getSimpleName(), this.gson.toJson(structurelevel));
		if(!plans.isEmpty())
			zipAdd(zos, Plan.class.getSimpleName(), this.gson.toJson(plans));
		
		if(!plandetailed.isEmpty())
			zipAdd(zos, PlanDetailed.class.getSimpleName(), this.gson.toJson(plandetailed));
		if(!attribute.isEmpty())
			zipAdd(zos, Attribute.class.getSimpleName(), this.gson.toJson(attribute));
		if(!structurelevelinstance.isEmpty())
			zipAdd(zos, StructureLevelInstance.class.getSimpleName(), this.gson.toJson(structurelevelinstance));
		if(!documentsection.isEmpty())
			zipAdd(zos, DocumentSection.class.getSimpleName(), this.gson.toJson(documentsection));
		

		if(!actionplan.isEmpty())
			zipAdd(zos, ActionPlan.class.getSimpleName(), this.gson.toJson(actionplan));
		if(!levelinstancehistory.isEmpty())
			zipAdd(zos, LevelInstanceHistory.class.getSimpleName(), this.gson.toJson(levelinstancehistory));
		if(!slid.isEmpty())
			zipAdd(zos, StructureLevelInstanceDetailed.class.getSimpleName(), this.gson.toJson(slid));
		if(!budget.isEmpty())
			zipAdd(zos, Budget.class.getSimpleName(), this.gson.toJson(budget));
		if(!attributeinstance.isEmpty())
			zipAdd(zos, AttributeInstance.class.getSimpleName(), this.gson.toJson(attributeinstance));
		if(!documentattribute.isEmpty())
			zipAdd(zos, DocumentAttribute.class.getSimpleName(), this.gson.toJson(documentattribute));
		if(!aggregateindicator.isEmpty())
		zipAdd(zos, AggregateIndicator.class.getSimpleName(), this.gson.toJson(aggregateindicator));
		
		zos.close();

		return bos.toByteArray();
	}

	/**
	 * adiciona dados ao banco a partir do arquivo
	 * 
	 * @param file arquivo a ser importado
	 * 
	 * @throws IOException
	 * @throws             org.json.simple.parser.ParseException
	 * 
	 */
	public void restore(UploadedFile file) throws IOException, org.json.simple.parser.ParseException {
		Company company = this.domain.getCompany();
		// company passado na hora da importação
		if (company == null) {
			throw new IllegalArgumentException("Company not found.");
		}
		
		List<File> files = decompact(file.getFile());
		Map<Long, Long> map_id_company = new HashMap<Long, Long>();
		Map<Long, Long> map_id_plan_macro = new HashMap<Long, Long>();
		Map<Long, Long> map_id_documento = new HashMap<Long, Long>();
		Map<Long, Long> map_id_documento_section = new HashMap<Long, Long>();
		Map<Long, Long> map_id_structure = new HashMap<Long, Long>();
		Map<Long, Long> map_id_structure_level = new HashMap<Long, Long>();
		Map<Long, Long> map_id_structure_level_instance = new HashMap<Long, Long>();
		Map<Long, Long> map_id_plan = new HashMap<Long, Long>();
		Map<Long, Long> map_budget_element = new HashMap<Long, Long>();
		Map<Long, Long> map_attribute = new HashMap<Long, Long>();
		
		
		for (File f : files) {

			String register = IOUtils.toString(new FileInputStream(f), StandardCharsets.UTF_8);
			Long id_old;
			String name = f.getName().split(".json")[0];
			JSONParser parser = new JSONParser();
			JSONArray array;

			switch (name) {
			
			case "Company" :
				Company c = gson.fromJson(register, Company.class);
				map_id_company.put(c.getId(), company.getId());
				break;
				
			case "PlanMacro":
				readJson(register, PlanMacro.class, map_id_plan_macro, map_id_company, null);
				break;

			case "Structure":
				array = (JSONArray) parser.parse(register);
				for (int i = 0; i < array.size(); i++) {
					Structure st = gson.fromJson(array.get(i).toString(), Structure.class);
					id_old = st.getId();
					st.setCompany(company);
					st.setId(null);
					this.dao.persist(st);
					map_id_structure.put(id_old, st.getId());
				}
				break;
				
			case "BudgetElement" :
				array = (JSONArray) parser.parse(register);
				for (int i = 0; i < array.size(); i++) {
					BudgetElement be = gson.fromJson(array.get(i).toString(), BudgetElement.class);
					id_old = be.getId();
					be.setCompany(company);
					be.setId(null);
					this.dao.persist(be);
					map_budget_element.put(id_old, be.getId());
				}
				break;

			case "Plan":
				readJson(register, Plan.class, map_id_plan, map_id_structure, map_id_plan_macro);
				break;
				
			case "PlanDetailed":
				readJson(register, PlanDetailed.class, null, map_id_plan, null);
				break;

			case "StructureLevel":
				readJson(register, StructureLevel.class, map_id_structure_level, map_id_structure, null);
				break;
				
			case "StructureLevelInstance":
				readJson(register, StructureLevelInstance.class, map_id_structure_level_instance, map_id_structure_level, map_id_plan);
				break;

			case "Document":
				readJson(register, Document.class, map_id_documento, map_id_plan_macro, null);
				break;
				
			case "DocumentSection":
				readJson(register, DocumentSection.class, map_id_documento_section, map_id_documento_section, map_id_documento);
				break;
				
			case "DocumentAttribute":
				readJson(register, DocumentAttribute.class, null, map_id_documento_section, null);
				break;
						
			case "ActionPlan" :
				readJson(register, ActionPlan.class, null, map_id_structure_level_instance, null);
				break;
				
			case "LevelInstanceHistory" :
				readJson(register, LevelInstanceHistory.class, null, map_id_structure_level_instance, null);
				break;	
				
			case "StructureLevelInstanceDetailed" :
				readJson(register, StructureLevelInstanceDetailed.class, null, map_id_structure_level_instance, null);
				break;
				
			case "Budget" :
				readJson(register, Budget.class, null, map_id_structure_level_instance, map_budget_element);
				break;
			case "Attribute" :
				readJson(register, Attribute.class, map_attribute, map_id_structure_level, null);
				break;	
				
			case "AttributeInstance" :
				readJson(register, AttributeInstance.class, null, map_id_structure_level_instance, map_attribute);
				break;
				
			case "AggregateIndicator" :
				readJson(register, AggregateIndicator.class, null, map_id_structure_level_instance, null);
				break;
			default:
				break;
			}
		}
	}

	
	/**
	 * adiciona dados ao banco a partir do json
	 * 
	 * @param register   json do registro
	 *
	 * @param clazz      classe a ser lida
	 * 
	 * @param map_atual  mapa do id da classe que está sendo importada
	 * 
	 * @param map_fkey_1 mapa do id da primeira foreign key desta classe
	 * 
	 * @param map_fkey_2 mapa do id da segunda foreign key desta classe
	 * 
	 * @throws org.json.simple.parser.ParseException
	 * 
	 */
	private void readJson(String register, Class<?> clazz, Map<Long, Long>  map_atual , Map<Long, Long> map_fkey_1,Map<Long, Long> map_fkey_2) throws org.json.simple.parser.ParseException {
		JSONParser parser = new JSONParser();
		JSONArray array = (JSONArray) parser.parse(register);
		
		if(clazz.getSimpleName().equals("StructureLevelInstance")) {
	
			List<StructureLevelInstance> sli = new ArrayList<>(); 
			
			for (int i = 0; i < array.size(); i++) {
				JSONObject jo = (JSONObject) array.get(i);
				StructureLevelInstance obj = (StructureLevelInstance) gson.fromJson(jo.toString(), clazz);
				sli.add(obj);
			}
			
			Map<Long, StructureLevel> structureLevelMap = new HashMap<>();
			List<StructureLevel> sl = retrieve(StructureLevel.class);
			sl.stream().forEach(it->{
				structureLevelMap.put(it.getId(), it);
			});
			
			Map<Long, Plan> planMap = new HashMap<>();
			List<Plan> pl = retrieve(Plan.class);
			pl.stream().forEach(it->{
				planMap.put(it.getId(), it);
			});
			
			do {
				for( StructureLevelInstance s : sli) {
					if(s.getParent()==null || map_atual.containsKey(s.getParent())) {
						
						s.setLevel(structureLevelMap.get(map_fkey_1.get(s.getExportLevelId())));
						s.setPlan( planMap.get(map_fkey_2.get(s.getExportPlanId())));					
						long id_old=s.getId();
						
						((SimpleLogicalDeletableEntity) s).setId(null);

						if(map_atual.containsKey(s.getParent())) {
							s.setParent(map_atual.get(s.getParent()));
						}
						
						s.setId(null);
						this.dao.persist((Serializable) s);

						if(map_atual !=null) {
							map_atual.put(id_old, ((SimpleLogicalDeletableEntity) s).getId());
						}				
						sli.remove(s);
						break;
					}
				}
			} while(!sli.isEmpty());
			return;
		}
		
		
		
		Map<Long, StructureLevelInstance> structureLevelInstanceMap = new HashMap<>();
		Map<Long, Attribute> attributeMap = new HashMap<>();
				
		if(clazz.getSimpleName().equals("AttributeInstance")) {
			
			List<StructureLevelInstance> sli = retrieve(StructureLevelInstance.class);
			sli.stream().forEach(it->{
				structureLevelInstanceMap.put(it.getId(), it);
			});
			
			List<Attribute> attr = retrieve(Attribute.class);
			attr.stream().forEach(it->{
				attributeMap.put(it.getId(), it);
			});
		}
		

		for (int i = 0; i < array.size(); i++) {

			JSONObject jo = (JSONObject) array.get(i);

			Object obj = gson.fromJson(jo.toString(), clazz);
			long id_old = 0;
			long id_old_structure;
			long id_old_planmacro;
			long id_old_structure_level;
			long id_old_structure_level_instance;
			long id_old_plan;
			long id_old_document_section;
			Criteria criteria;

			switch (clazz.getSimpleName()) {
			
				case "PlanMacro" :

					long id_old_company = Long.parseLong(jo.get("exportCompanyId").toString());
					
					criteria = this.dao.newCriteria(Company.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_company)));
					((PlanMacro) obj).setCompany((Company) criteria.uniqueResult());
					id_old=((PlanMacro) obj).getId();
			
					break;
					
				case "Plan":
					
					id_old_structure = Long.parseLong(jo.get("exportStructureId").toString());
					id_old_planmacro = Long.parseLong(jo.get("exportPlanMacroId").toString());
					
					criteria = this.dao.newCriteria(Structure.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_structure)));
					((Plan) obj).setStructure((Structure) criteria.uniqueResult());
		
					criteria = this.dao.newCriteria(PlanMacro.class);
					criteria.add(Restrictions.eq("id", map_fkey_2.get(id_old_planmacro)));
					((Plan) obj).setParent((PlanMacro) criteria.uniqueResult());
					id_old=((Plan) obj).getId();
					break;	
					
				case "PlanDetailed":
		
					id_old_plan = Long.parseLong(jo.get("exportPlanId").toString());
		
					criteria = this.dao.newCriteria(Plan.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_plan)));
					((PlanDetailed) obj).setPlan((Plan) criteria.uniqueResult());
					break;	
					
				case "Document":

					id_old_planmacro = Long.parseLong(jo.get("exportPlanMacroId").toString());
					
					criteria = this.dao.newCriteria(PlanMacro.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_planmacro)));
					((Document) obj).setPlan((PlanMacro) criteria.uniqueResult());
					id_old=((Document) obj).getId();
					break;
					
				case "DocumentSection":
					
					id_old_document_section = Long.parseLong(jo.get("exportDocumentSectionId").toString());
					long id_old_document = Long.parseLong(jo.get("exportDocumentId").toString());
					
					criteria = this.dao.newCriteria(DocumentSection.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_document_section)));
					((DocumentSection) obj).setParent((DocumentSection) criteria.uniqueResult());
					
					criteria = this.dao.newCriteria(Document.class);
					criteria.add(Restrictions.eq("id", map_fkey_2.get(id_old_document)));
					((DocumentSection) obj).setDocument((Document) criteria.uniqueResult());
					id_old=((DocumentSection) obj).getId();
					break;
					
				case "DocumentAttribute":
					
					id_old_document_section = Long.parseLong(jo.get("exportDocumentSectionId").toString());
					criteria = this.dao.newCriteria(DocumentSection.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_document_section)));
					((DocumentAttribute) obj).setSection((DocumentSection) criteria.uniqueResult());
					break;
					
				case "StructureLevel":
					
					id_old_structure = Long.parseLong(jo.get("exportStructureId").toString());
	
					criteria = this.dao.newCriteria(Structure.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_structure)));
					((StructureLevel) obj).setStructure((Structure) criteria.uniqueResult());
					id_old=((StructureLevel) obj).getId();
					break;
					
				case "ActionPlan" :

					id_old_structure_level_instance = Long.parseLong(jo.get("exportStructureLevelInstanceId").toString());
					
					criteria = this.dao.newCriteria(StructureLevelInstance.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_structure_level_instance)));
					((ActionPlan) obj).setLevelInstance((StructureLevelInstance) criteria.uniqueResult());
					break;
					
				case "LevelInstanceHistory" :

					id_old_structure_level_instance = Long.parseLong(jo.get("exportStructureLevelInstanceId").toString());
					
					criteria = this.dao.newCriteria(StructureLevelInstance.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_structure_level_instance)));
					((LevelInstanceHistory) obj).setLevelInstance((StructureLevelInstance) criteria.uniqueResult());
					break;
					
				case "StructureLevelInstanceDetailed" :
					
					id_old_structure_level_instance = Long.parseLong(jo.get("exportStructureLevelInstanceId").toString());
					
					criteria = this.dao.newCriteria(StructureLevelInstance.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_structure_level_instance)));
					((StructureLevelInstanceDetailed) obj).setLevelInstance((StructureLevelInstance) criteria.uniqueResult());
					break;
					
				case "Budget" :
					
					id_old_structure_level_instance = Long.parseLong(jo.get("exportStructureLevelInstanceId").toString());
					long id_old_budget_element = Long.parseLong(jo.get("exportBudgetElementId").toString());
					
					criteria = this.dao.newCriteria(StructureLevelInstance.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_structure_level_instance)));
					((Budget) obj).setLevelInstance((StructureLevelInstance) criteria.uniqueResult());
	
					criteria = this.dao.newCriteria(BudgetElement.class);
					criteria.add(Restrictions.eq("id", map_fkey_2.get(id_old_budget_element)));
					((Budget) obj).setBudgetElement((BudgetElement) criteria.uniqueResult());
					break;

				case "Attribute" :
					
					id_old_structure_level = Long.parseLong(jo.get("exportStructureLevelId").toString());
										
					criteria = this.dao.newCriteria(StructureLevel.class);
					criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_structure_level)));
					((Attribute) obj).setLevel((StructureLevel) criteria.uniqueResult());
					id_old=((Attribute) obj).getId();
					break;
					
				case "AttributeInstance" :
					
					id_old_structure_level_instance = Long.parseLong(jo.get("exportStructureLevelInstanceId").toString());
					long id_old_attribute = Long.parseLong(jo.get("exportAttributeId").toString());
					
					//atualiza id do responsável que possui o mesmo email neste banco 
					if(attributeMap.get(map_fkey_2.get(id_old_attribute)).getLabel().equals("Responsável")) {
						String email=structureLevelInstanceMap.get(map_fkey_1.get(id_old_structure_level_instance)).getExportResponsibleMail();
						User user=userBS.existsByEmail(email);
						if (user == null) {
							continue;
						}
						((AttributeInstance) obj).setValue(String.valueOf(user.getId()));
					}
					
					((AttributeInstance) obj).setLevelInstance(structureLevelInstanceMap.get(map_fkey_1.get(id_old_structure_level_instance)));
					((AttributeInstance) obj).setAttribute(attributeMap.get(map_fkey_2.get(id_old_attribute)));
					break;

				case "AggregateIndicator" :
					
					long id_old_aggregated = Long.parseLong(jo.get("exportAggregateId").toString());
					long id_old_indicator = Long.parseLong(jo.get("exportIndicatorId").toString());
					
					criteria = this.dao.newCriteria(StructureLevelInstance.class);
					StructureLevelInstance id_new_aggregated = (StructureLevelInstance) criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_aggregated))).uniqueResult();
					((AggregateIndicator) obj).setAggregate(id_new_aggregated);
					
					criteria = this.dao.newCriteria(StructureLevelInstance.class);
					StructureLevelInstance id_new_indicator = (StructureLevelInstance)  criteria.add(Restrictions.eq("id", map_fkey_1.get(id_old_indicator))).uniqueResult();
					((AggregateIndicator) obj).setIndicator(id_new_indicator);			
					break;
			}

			((SimpleLogicalDeletableEntity) clazz.cast(obj)).setId(null);

			this.dao.persist((Serializable) clazz.cast(obj));
			
			if(map_atual !=null) {
				map_atual.put(id_old, ((SimpleLogicalDeletableEntity) clazz.cast(obj)).getId());
			}
		}
	}
	
	/**
	 * Recuperar Todos os arquivo do zip
	 * 
	 * @param InputStream arquivo do upload
	 * 
	 * @return List<File> lista de arquivos do zip
	 * 
	 * @throws IOException
	 *
	 */
	private List<File> decompact(InputStream inputStream) throws IOException {

		List<File> files = new ArrayList<File>();
		byte[] buffer = new byte[1024];
		ZipInputStream zis = new ZipInputStream(inputStream);
		ZipEntry zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			String fileName = zipEntry.getName().split("_")[0];
			File newFile = File.createTempFile(fileName + "_", ".temp");
			FileOutputStream fos = new FileOutputStream(newFile);
			int len;
			while ((len = zis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			files.add(newFile);
			zipEntry = zis.getNextEntry();
		}
		zis.closeEntry();
		zis.close();

		return files;
	}
	
	/**
	 * Adiciona arquivo no zip
	 * 
	 * @param ZipOutputStream arquivo zip
	 * 
	 * @param className       nome da classe
	 * 
	 * @param content         json a ser salvo
	 * 
	 * @throws IOException
	 *
	 */
	private void zipAdd(ZipOutputStream zos, String className, String content) throws IOException {
		ZipEntry entry = new ZipEntry(String.format("%s.json", className));
		zos.putNextEntry(entry);
		zos.write(content.getBytes("UTF-8"));
		zos.closeEntry();
	}

	/**
	 * Lista Modelo
	 * 
	 * @param List<clazz> Classe do modelo no banco
	 * 
	 * @return String resultado da busca
	 */
	private <clazz> List<clazz> retrieve(Class clazz) {
		List<clazz> modelo = this.dao.newCriteria(clazz).list();
		return modelo;
	}

}