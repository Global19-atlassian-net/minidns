/*
 * Copyright 2015-2018 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package org.minidns.hla;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.minidns.AbstractDnsClient.IpVersionSetting;
import org.minidns.MiniDnsException.NullResultException;
import org.minidns.dnsname.DnsName;
import org.minidns.hla.srv.SrvServiceProto;
import org.minidns.record.A;
import org.minidns.record.AAAA;
import org.minidns.record.InternetAddressRR;
import org.minidns.record.SRV;
import org.minidns.util.SrvUtil;

public class SrvResolverResult extends ResolverResult<SRV> {

    private final ResolverApi resolver;
    private final IpVersionSetting ipVersion;
    private final SrvServiceProto srvServiceProto;

    private List<ResolvedSrvRecord> sortedSrvResolvedAddresses;

    SrvResolverResult(ResolverResult<SRV> srvResult, SrvServiceProto srvServiceProto, ResolverApi resolver) throws NullResultException {
        super(srvResult.question, srvResult.result, srvResult.unverifiedReasons);
        this.resolver = resolver;
        this.ipVersion = resolver.getClient().getPreferedIpVersion();
        this.srvServiceProto = srvServiceProto;
    }

    public List<ResolvedSrvRecord> getSortedSrvResolvedAddresses() throws IOException {
        if (sortedSrvResolvedAddresses != null) {
            return sortedSrvResolvedAddresses;
        }

        throwIseIfErrorResponse();

        List<SRV> srvRecords = SrvUtil.sortSrvRecords(getAnswers());

        List<ResolvedSrvRecord> res = new ArrayList<>(srvRecords.size());
        for (SRV srvRecord : srvRecords) {
            ResolverResult<A> aRecordsResult = null;
            ResolverResult<AAAA> aaaaRecordsResult = null;
            Set<A> aRecords = Collections.emptySet();
            if (ipVersion.v4) {
                aRecordsResult = resolver.resolve(srvRecord.target, A.class);
                if (aRecordsResult.wasSuccessful() && !aRecordsResult.hasUnverifiedReasons()) {
                    aRecords = aRecordsResult.getAnswers();
                }
            }

            Set<AAAA> aaaaRecords = Collections.emptySet();
            if (ipVersion.v6) {
                aaaaRecordsResult = resolver.resolve(srvRecord.target, AAAA.class);
                if (aaaaRecordsResult.wasSuccessful() && !aaaaRecordsResult.hasUnverifiedReasons()) {
                    aaaaRecords = aaaaRecordsResult.getAnswers();
                }
            }

            if (aRecords.isEmpty() && aaaaRecords.isEmpty()) {
                // TODO Possibly check for (C|D)NAME usage and throw a meaningful exception that it is not allowed for
                // the target of an SRV to be an alias as per RFC 2782.
                /*
                ResolverResult<CNAME> cnameRecordResult = resolve(srvRecord.name, CNAME.class);
                if (cnameRecordResult.wasSuccessful()) {
                }
                */
                continue;
            }

            List<InternetAddressRR> srvAddresses = new ArrayList<>(aRecords.size() + aaaaRecords.size());
            switch (ipVersion) {
            case v4only:
                srvAddresses.addAll(aRecords);
                break;
            case v6only:
                srvAddresses.addAll(aaaaRecords);
                break;
            case v4v6:
                srvAddresses.addAll(aRecords);
                srvAddresses.addAll(aaaaRecords);
                break;
            case v6v4:
                srvAddresses.addAll(aaaaRecords);
                srvAddresses.addAll(aRecords);
                break;
            }

            ResolvedSrvRecord resolvedSrvAddresses = new ResolvedSrvRecord(question.name, srvServiceProto, srvRecord, srvAddresses,
                    aRecordsResult, aaaaRecordsResult);
            res.add(resolvedSrvAddresses);
        }

        sortedSrvResolvedAddresses = res;

        return res;
    }

    public static class ResolvedSrvRecord {
        public final DnsName name;
        public final SrvServiceProto srvServiceProto;
        public final SRV srv;
        public final List<InternetAddressRR> addresses;
        public final ResolverResult<A> aRecordsResult;
        public final ResolverResult<AAAA> aaaaRecordsResult;

        /**
         * The port announced by the SRV RR. This is simply a shortcut for <code>srv.port</code>.
         */
        public final int port;

        private ResolvedSrvRecord(DnsName name, SrvServiceProto srvServiceProto, SRV srv,
                List<InternetAddressRR> addresses, ResolverResult<A> aRecordsResult,
                ResolverResult<AAAA> aaaaRecordsResult) {
            this.name = name;
            this.srvServiceProto = srvServiceProto;
            this.srv = srv;
            this.addresses = Collections.unmodifiableList(addresses);
            this.port = srv.port;
            this.aRecordsResult = aRecordsResult;
            this.aaaaRecordsResult = aaaaRecordsResult;
        }
    }
}
