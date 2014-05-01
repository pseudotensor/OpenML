/*
 *  Webapplication - Java library that runs on OpenML servers
 *  Copyright (C) 2014 
 *  @author Jan N. van Rijn (j.n.van.rijn@liacs.leidenuniv.nl)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */
package org.openml.webapplication.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openml.apiconnector.algorithms.Conversion;
import org.openml.apiconnector.io.ApiConnector;
import org.openml.apiconnector.io.ApiSessionHash;
import org.openml.apiconnector.settings.Config;
import org.openml.apiconnector.xml.DataQuality;
import org.openml.apiconnector.xml.DataQuality.Quality;
import org.openml.apiconnector.xml.DataSetDescription;
import org.openml.apiconnector.xstream.XstreamXmlMapping;
import org.openml.webapplication.fantail.dc.Characterizer;
import org.openml.webapplication.fantail.dc.StreamCharacterizer;
import org.openml.webapplication.fantail.dc.landmarking.J48BasedLandmarker;
import org.openml.webapplication.fantail.dc.landmarking.REPTreeBasedLandmarker;
import org.openml.webapplication.fantail.dc.landmarking.RandomTreeBasedLandmarker;
import org.openml.webapplication.fantail.dc.landmarking.SimpleLandmarkers;
import org.openml.webapplication.fantail.dc.statistical.AttributeCount;
import org.openml.webapplication.fantail.dc.statistical.AttributeEntropy;
import org.openml.webapplication.fantail.dc.statistical.AttributeType;
import org.openml.webapplication.fantail.dc.statistical.ClassAtt;
import org.openml.webapplication.fantail.dc.statistical.DefaultAccuracy;
import org.openml.webapplication.fantail.dc.statistical.IncompleteInstanceCount;
import org.openml.webapplication.fantail.dc.statistical.InstanceCount;
import org.openml.webapplication.fantail.dc.statistical.MissingValues;
import org.openml.webapplication.fantail.dc.statistical.NominalAttDistinctValues;
import org.openml.webapplication.fantail.dc.statistical.Statistical;
import org.openml.webapplication.fantail.dc.stream.ChangeDetectors;

import com.thoughtworks.xstream.XStream;

import weka.core.Instances;
import weka.core.converters.ArffLoader;

public class FantailConnector {
	
	private static final XStream xstream = XstreamXmlMapping.getInstance();
	private static final Characterizer[] batchCharacterizers = {
			new Statistical(), new AttributeCount(), new AttributeType(),
			new ClassAtt(), new DefaultAccuracy(),
			new IncompleteInstanceCount(), new InstanceCount(),
			new MissingValues(), new NominalAttDistinctValues(),
			new AttributeEntropy(), new SimpleLandmarkers(),
			new J48BasedLandmarker(), new REPTreeBasedLandmarker(),
			new RandomTreeBasedLandmarker() 
	};
	
	private static StreamCharacterizer[] streamCharacterizers;
	
	public static void main( String[] args ) throws Exception {
		extractFeatures( 1, "class", null, new Config( "username = janvanrijn@gmail.com; password = Feyenoord2002; server = http://localhost/") );
	}
	
	public static boolean extractFeatures(Integer did, String datasetClass, Integer interval_size,
			Config config) throws Exception {
		// TODO: initialize this properly!!!!!!
		streamCharacterizers = new StreamCharacterizer[1]; 
		streamCharacterizers[0] = new ChangeDetectors( interval_size );
		
		//List<String> prevCalcQualities;
		ApiConnector apiconnector;
		
		if( config.getServer() != null ) {
			apiconnector = new ApiConnector( config.getServer() );
		} else { 
			apiconnector = new ApiConnector();
		} 
		
		DataSetDescription dsd = apiconnector.openmlDataDescription(did);
		
		/*try {
			if( interval_size == null ) {
				DataQuality apiQualities = apiconnector.openmlDataQuality(did);
				prevCalcQualities = Arrays.asList( apiQualities.getQualityNames() );
				// TODO: do this check also for interval sizes
			} else {
				prevCalcQualities = new ArrayList<String>();
			}
		} catch( Exception e ) {
			// no qualities calculated yet. We might want to avoid catching this error
			prevCalcQualities = new ArrayList<String>();
		}
		
		List<String> uncalculatedQualities = characteristicsAvailable();
		uncalculatedQualities.removeAll( prevCalcQualities );
		
		if( uncalculatedQualities.size() == 0 ) {
			System.out.println(Output.statusMessage("OK", "No new Fantail Features from data #" + did));
			return;
		}*/
		
		ArffLoader datasetLoader = new ArffLoader();
		datasetLoader.setURL(dsd.getUrl());
		Instances dataset = new Instances( datasetLoader.getDataSet() );
		dataset.setClass( dataset.attribute( datasetClass ) );
		
		

		// first run stream characterizers
		for( StreamCharacterizer sc : streamCharacterizers ) {
			sc.characterize( dataset );
		}
		
		List<Quality> qualities = new ArrayList<DataQuality.Quality>();
		if( interval_size != null ) {
			for( int i = 0; i < dataset.numInstances(); i += interval_size ) {
				qualities.addAll( datasetCharacteristics( dataset, i, interval_size ) );
				
				for( StreamCharacterizer sc : streamCharacterizers ) {
					qualities.addAll( hashMaptoList( sc.interval( i ), i, interval_size ) );
				}
			}
			
		} else {
			qualities.addAll( datasetCharacteristics( dataset, null, null ) );
			for( StreamCharacterizer sc : streamCharacterizers ) {
				qualities.addAll( hashMaptoList( sc.global( ), null, null ) );
			}
		}

		DataQuality dq = new DataQuality(did, qualities.toArray( new Quality[qualities.size()] ) );
		String strQualities = xstream.toXML(dq);
		ApiSessionHash ash = new ApiSessionHash( apiconnector );
		ash.set(config.getUsername(), config.getPassword());
		
		apiconnector.openmlDataQualityUpload(
				Conversion.stringToTempFile(strQualities, "qualities_did_"
						+ did, "xml"), ash.getSessionHash());
		
		return true;
	}

	/*private static List<String> characteristicsAvailable() {
		List<String> allCharacteristics = new ArrayList<String>();
		
		for( Characterizer dc : batchCharacterizers ) {
			allCharacteristics.addAll( Arrays.asList( dc.getIDs() ) );
		}
		
		return allCharacteristics;
	}*/

	private static List<Quality> datasetCharacteristics( Instances fulldata, Integer start, Integer interval_size ) throws Exception {
		List<Quality> result = new ArrayList<DataQuality.Quality>();
		Instances intervalData;
		
		// Be careful changing this!
		if( interval_size != null ) {
			intervalData = new Instances( fulldata, start, Math.min( interval_size, fulldata.numInstances() - start ) );
		} else {
			intervalData = fulldata;
		}
		
		for( Characterizer dc : batchCharacterizers ) {
			result.addAll( hashMaptoList( dc.characterize(intervalData), start, interval_size ) );
			
		}
		
		return result;
	}
	
	public static List<Quality> hashMaptoList( Map<String, Double> map, Integer start, Integer size ) {
		List<Quality> result = new ArrayList<DataQuality.Quality>();
		for( String quality : map.keySet() ) {
			Integer end = start != null ? start + size : null;
			result.add( new Quality( quality, map.get( quality ) + "", start, end ) );
		}
		return result;
	}
}
