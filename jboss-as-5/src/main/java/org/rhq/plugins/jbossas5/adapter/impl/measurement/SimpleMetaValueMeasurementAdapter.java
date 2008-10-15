 /*
  * Jopr Management Platform
  * Copyright (C) 2005-2008 Red Hat, Inc.
  * All rights reserved.
  *
  * This program is free software; you can redistribute it and/or modify
  * it under the terms of the GNU General Public License, version 2, as
  * published by the Free Software Foundation, and/or the GNU Lesser
  * General Public License, version 2.1, also as published by the Free
  * Software Foundation.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License and the GNU Lesser General Public License
  * for more details.
  *
  * You should have received a copy of the GNU General Public License
  * and the GNU Lesser General Public License along with this program;
  * if not, write to the Free Software Foundation, Inc.,
  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
  */
package org.rhq.plugins.jbossas5.adapter.impl.measurement;

import org.jboss.metatype.api.values.MetaValue;
import org.jboss.metatype.api.values.SimpleValueSupport;
import org.rhq.core.domain.measurement.DataType;
import org.rhq.core.domain.measurement.MeasurementDataNumeric;
import org.rhq.core.domain.measurement.MeasurementDataTrait;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.plugins.jbossas5.adapter.api.MeasurementAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SimpleMetaValueMeasurementAdapter implements MeasurementAdapter
{
    private final Log LOG = LogFactory.getLog(SimpleMetaValueMeasurementAdapter.class);

    public void setMeasurementData(MeasurementReport report, MetaValue metaValue, MeasurementScheduleRequest request, MeasurementDefinition measurementDefinition)
    {
        SimpleValueSupport simpleValue = (SimpleValueSupport) metaValue;
        DataType dataType = measurementDefinition.getDataType();
        if (dataType.equals(DataType.MEASUREMENT))
        {
            try
            {
                MeasurementDataNumeric dataNumeric = new MeasurementDataNumeric(request,  new Double(simpleValue.getValue().toString()));
                report.addData(dataNumeric);
            }
            catch (NumberFormatException e)
            {
                LOG.warn("Measurement request: " + request.getName() + " did not return a numeric value from the Profile Service", e);
            }

        }
        else if (dataType.equals(DataType.TRAIT))
        {
            MeasurementDataTrait dataTrait = new MeasurementDataTrait(request, (String) simpleValue.getValue());
            report.addData(dataTrait);
        }
    }
}
