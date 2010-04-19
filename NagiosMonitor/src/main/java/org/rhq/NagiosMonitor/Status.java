package org.rhq.NagiosMonitor;
/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

import java.util.Hashtable;

/**
 * Class implements a Nagios status
 * 
 * @author Alexander Kiefer
 */
public class Status 
{
	private String id;
	private Hashtable<String, Metric> metricTable;
	
	public Status()
	{
		//TODO ??
		this.id = "Status";
	}
	
	public String getId() 
	{
		return id;
	}
	
	public void setId(String id) 
	{
		this.id = id;
	}
	
	public Hashtable<String, Metric> getMetricTable() 
	{
		return metricTable;
	}
	
	public void setMetricTable(Hashtable<String, Metric> metricTable) 
	{
		this.metricTable = metricTable;
	}
}
