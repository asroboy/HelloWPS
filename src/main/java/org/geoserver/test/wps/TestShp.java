/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoserver.test.wps;

import org.geotools.process.vector.IntersectionFeatureCollection;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.data.DataStore;
import org.geotools.data.*;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.filter.FilterFactoryImpl;
import static org.geotools.filter.text.commons.Language.CQL;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Expression;

/**
 *
 * @author asrofiridho
 */
public class TestShp {

    public static void main(String[] args) {
        
        test();
//        try {
//
//            DataStore dataStoreUji = readDataStore("D:\\Kerjaan\\Pak Ari BIG\\Data\\Data\\BANGUNAN_SEDANG_UJI.shp");
//            String typeNameUji = dataStoreUji.getTypeNames()[0];
//            FeatureSource<SimpleFeatureType, SimpleFeature> source
//                    = dataStoreUji.getFeatureSource(typeNameUji);
//            Filter filter = Filter.INCLUDE; // ECQL.toFilter("BBOX(THE_GEOM, 10,20,30,40)")
//            FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);
////            try (FeatureIterator<SimpleFeature> features = collection.features()) {
////                while (features.hasNext()) {
////                    SimpleFeature feature = features.next();
////                    System.out.print(feature.getID());
////                    System.out.print(": ");
////                    System.out.println(feature.getDefaultGeometryProperty().getValue());
////                }
////            }
//
//            Query query = new Query(typeNameUji);
//
//            DataStore dataStorePembanding = readDataStore("D:\\Kerjaan\\Pak Ari BIG\\Data\\Data\\BANGUNAN_SEDANG_PY_REF.shp");
//            String typeNamePembanding = dataStorePembanding.getTypeNames()[0];
//            FeatureSource<SimpleFeatureType, SimpleFeature> sourcePembanding
//                    = dataStorePembanding.getFeatureSource(typeNamePembanding);
//            Filter filterPembanding = Filter.INCLUDE; // ECQL.toFilter("BBOX(THE_GEOM, 10,20,30,40)")
//            FeatureCollection<SimpleFeatureType, SimpleFeature> collectionPembanding = source.getFeatures(filterPembanding);
//
//            List<Join> joins = query.getJoins();
//
//            FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
//
//            try (FeatureIterator<SimpleFeature> features = collectionPembanding.features()) {
//                while (features.hasNext()) {
//                    SimpleFeature feature = features.next();
////                    System.out.print(feature.getID());
////                    System.out.print(": ");
////                    System.out.println(feature.getDefaultGeometryProperty().getValue());
//                    Join join = new Join(typeNamePembanding, filterPembanding);
//                    joins.add(join);
//
//                }
//            }
//
//            System.out.print("joins size " + joins.size());
//
//            FileDataStoreFactorySpi factory = new ShapefileDataStoreFactory();
//
//            File file = new File("D:\\Kerjaan\\Pak Ari BIG\\Data\\Data\\test.shp");
//
//            Map map = Collections.singletonMap("url", file.toURI().toURL());
//
//            DataStore myData = factory.createNewDataStore(map);
//            SimpleFeatureType featureType
//                    = DataUtilities.createType(
//                            "joined", "geom:Polygon,name:String");
//
//            myData.createSchema(featureType);
//
//            for (Join j : joins) {
//                System.out.print("join " + j.getType().name());
//
//            }
//
//        } catch (MalformedURLException ex) {
//            Logger.getLogger(TestShp.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (IOException ex) {
//            Logger.getLogger(TestShp.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (SchemaException ex) {
//            Logger.getLogger(TestShp.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }

    static DataStore readDataStore(String path) throws MalformedURLException, IOException {
        File file = new File(path);
        Map<String, Object> map = new HashMap<>();
        map.put("url", file.toURI().toURL());

        DataStore dataStore = DataStoreFinder.getDataStore(map);
        return dataStore;
    }

    static void test() {
        try {
            String url = "http://localhost:8080/geoserver/BIG_DEV/wms?service=WMS&version=1.1.0&&request=GetMap";
          
            Map connectionParameters = new HashMap();
            connectionParameters.put("WFSDataStoreFactory:GET_MAP", url);
            WFSDataStoreFactory dsf = new WFSDataStoreFactory();

            WFSDataStore dataStore = dsf.createDataStore(connectionParameters);
            SimpleFeatureSource sourceBANGUNAN_SEDANG_PY_REF = dataStore.getFeatureSource("BIG_DEV:BANGUNAN_SEDANG_PY_REF");
            SimpleFeatureSource sourceBANGUNAN_SEDANG_UJI = dataStore.getFeatureSource("BIG_DEV:BANGUNAN_SEDANG_UJI");
            SimpleFeatureCollection firstFeatures = sourceBANGUNAN_SEDANG_PY_REF.getFeatures();
            SimpleFeatureCollection secondFeatures = sourceBANGUNAN_SEDANG_UJI.getFeatures();

            IntersectionFeatureCollection ifc = new IntersectionFeatureCollection();
            SimpleFeatureCollection output = ifc.execute(firstFeatures, secondFeatures, null, null, IntersectionFeatureCollection.IntersectionMode.INTERSECTION, Boolean.FALSE, Boolean.TRUE);

            if (!output.isEmpty()) {
                SimpleFeature sf = output.features().next();
                System.out.print(sf.getAttribute("geom"));

            }
        } catch (IOException ex) {
            Logger.getLogger(TestShp.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
