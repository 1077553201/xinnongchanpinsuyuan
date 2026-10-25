package org.nongchanpin.service;

import java.util.*;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.QualityEvidenceRequests.Replace;
import org.nongchanpin.model.entity.QualityEvidenceModels.Revision;
import org.nongchanpin.model.entity.SourceQualityModels.Report;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class QualityRevisionService {
    private final SourceQualityService quality;
    private final SourceQualityMapper reports;
    private final QualityRevisionMapper revisions;
    private final AuditService audit;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id,Long previousId){Report current=quality.checkedReport(id);Map<String,Object> value=new LinkedHashMap<>();value.put("report",current);value.put("previous",revisions.previous(id));value.put("next",revisions.next(id));if(previousId!=null){Report previous=quality.checkedReport(previousId);sameSource(previous,current);value.put("candidatePrevious",previous);}return value;}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Revision link(Long id,@Valid Replace request){
        AccessContext.write();Report observed=quality.checkedReport(id),old=quality.checkedReport(request.getPreviousReportId());sameSource(old,observed);reports.lockHarvest(observed.getHarvestId());
        Report previous=reports.lock(old.getId()),current=reports.lock(id);sameSource(previous,current);
        if(!previous.getManifestHash().equals(request.getExpectedPreviousHash())||!current.getManifestHash().equals(request.getExpectedReplacementHash()))throw new IllegalArgumentException("报告摘要与确认内容不一致，请刷新后核对。");
        Revision existing=revisions.previous(id);if(existing!=null){if(!existing.getPreviousReportId().equals(previous.getId())||!existing.getReason().equals(request.getReason().trim()))throw new IllegalArgumentException("本报告已有关联的原报告，不能覆盖原关系。");return existing;}
        if(revisions.next(previous.getId())!=null)throw new IllegalArgumentException("原报告已有替代记录，请沿原修订关系继续办理，不能分叉。");
        if(!"WITHDRAWN".equals(previous.getStatus())||!"DRAFT".equals(current.getStatus()))throw new IllegalArgumentException("原报告须先撤回，替代报告须尚未公开；关联不会自动公开新报告。");
        Revision row=new Revision();row.setPreviousReportId(previous.getId());row.setReplacementReportId(id);row.setReason(request.getReason().trim());row.setRecordedBy(AccessContext.require().getId());revisions.insert(row);audit.record("QUALITY_REPLACEMENT","QUALITY_REPORT",id,"原报告 #"+previous.getId()+" / "+row.getReason());return revisions.previous(id);
    }
    private void sameSource(Report previous,Report current){if(previous.getId()>=current.getId()||!Objects.equals(previous.getOrganizationId(),current.getOrganizationId())||!Objects.equals(previous.getScopeType(),current.getScopeType())||!Objects.equals(previous.getHarvestId(),current.getHarvestId())||!Objects.equals(previous.getPackingId(),current.getPackingId()))throw new IllegalArgumentException("替代报告须晚于原记录，且属于同一机构及完全相同的采收或包装范围。");}
}
