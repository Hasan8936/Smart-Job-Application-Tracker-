package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name="job_provider_syncs", uniqueConstraints=@UniqueConstraint(columnNames={"provider","query_key"}))
public class JobProviderSync {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String provider; @Column(name="query_key", nullable=false) private String queryKey;
    private String cursor; private String status; @Column(name="last_synced_at") private OffsetDateTime lastSyncedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getProvider(){return provider;} public void setProvider(String v){provider=v;} public String getQueryKey(){return queryKey;} public void setQueryKey(String v){queryKey=v;} public String getCursor(){return cursor;} public void setCursor(String v){cursor=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public OffsetDateTime getLastSyncedAt(){return lastSyncedAt;} public void setLastSyncedAt(OffsetDateTime v){lastSyncedAt=v;}
}