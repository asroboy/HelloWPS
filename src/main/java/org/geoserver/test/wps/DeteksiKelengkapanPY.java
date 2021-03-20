/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoserver.test.wps;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.vector.IntersectionFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author asrofiridho
 */
@DescribeProcess(title = "DeteksiKelengkapanPY", description = "Mendeteksi Omission dan Commission dengan data pembanding poligon")
public class DeteksiKelengkapanPY implements GeoServerProcess {

    private static final Logger logger;

    static {
        logger = Logger.getLogger("org.geoserver.test.wps.DeteksiKelengkapanPY");
    }

    private static final int GEOMETRY_PRECISION = 8;
    DataStore dataStore = null;

    public enum DeteksiKelengkapan {
        OMISSION,
        COMMISSION
    }

    public static void main(String[] args) throws IOException {
        try {
            String getCapabilities1 = "http://127.0.0.1:8080/geoserver/big_postgis/ows?service=WFS&version=1.0.0&request=GetCapabilities";
//            String getCapabilities2 = "http://127.0.0.1:8080/geoserver/big_postgis/ows?service=WFS&version=1.0.0&request=GetCapabilities";
            DeteksiKelengkapanPY me = new DeteksiKelengkapanPY(getCapabilities1);

            String[] names = me.getTypeNames();
            System.out.println("type names size " + names.length);
            for (String name : names) {
//                SimpleFeatureType schema = me.getSchema(name);
                System.out.println(name);
            }

            String typeName = "big_postgis:BANGUNAN_SEDANG_UJI";
            String typeName2 = "big_postgis:BANGUNAN_SEDANG_PY_REF";
            SimpleFeatureCollection firstFeatures = me.getFeatureCollection(typeName);
            SimpleFeatureCollection secondFeatures = me.getFeatureCollection(typeName2);

            IntersectionFeatureCollection ifc = new IntersectionFeatureCollection();
            DeteksiKelengkapanPY kp = new DeteksiKelengkapanPY("");
            SimpleFeatureCollection output = kp.execute(firstFeatures, secondFeatures, DeteksiKelengkapan.OMISSION);
            SimpleFeatureIterator sfi = output.features();
            if (sfi.hasNext()) {
                SimpleFeature feature = sfi.next();
                logger.log(Level.INFO, "geom " + ((Geometry) feature.getDefaultGeometry()).toText());
            }

        } catch (Exception ex) {
            Logger.getLogger(DeteksiKelengkapanPY.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    @DescribeResult(name = "result", description = "Output / Hasil")
    public SimpleFeatureCollection execute(@DescribeParameter(name = "Data Uji (feature collection)", description = "Data Uji (First feature collection)") SimpleFeatureCollection dataUji,
            @DescribeParameter(name = "Data Pembanding/Referensi (feature collection)", description = "Data Pembanding (Second feature collection)") SimpleFeatureCollection dataPembanding,
            @DescribeParameter(name = "Analisis", description = "Analisi yang dilakukan") DeteksiKelengkapan Analisis) throws IOException, Exception {

        final Class dataUjiGeomType = ((SimpleFeatureType) dataUji.getSchema()).getGeometryDescriptor().getType().getBinding();
        final Class dataPembandingGeomType = ((SimpleFeatureType) dataPembanding.getSchema()).getGeometryDescriptor().getType().getBinding();
        if (!isGeometryTypeIn(dataUjiGeomType, MultiPolygon.class, Polygon.class, MultiLineString.class, LineString.class)) {
            throw new IllegalArgumentException("Data Uji Harus Polygon");
        }
        if (!isGeometryTypeIn(dataPembandingGeomType, MultiPolygon.class, Polygon.class, MultiLineString.class, LineString.class)) {
            throw new IllegalArgumentException("Data Pembanding Harus Polygon");
        }
        List<String> attributes1 = new ArrayList<>();
        dataUji.getSchema().getAttributeDescriptors().forEach((at) -> {
            attributes1.add(at.getLocalName());
        });
        List<String> attributes2 = new ArrayList<>();
        dataPembanding.getSchema().getAttributeDescriptors().forEach((at) -> {
            attributes2.add(at.getLocalName());
        });
        IntersectionFeatureCollection ifc = new IntersectionFeatureCollection();

        SimpleFeatureCollection output = difference(dataUji, dataPembanding, Analisis, attributes1, attributes2);
        return output;
    }

    private static SimpleFeatureCollection difference(SimpleFeatureCollection sfc1, SimpleFeatureCollection sfc2,
            DeteksiKelengkapan Analisis,
            final List<String> attributes1, List<String> attributes2) throws Exception {

        SimpleFeatureCollection source = Analisis == DeteksiKelengkapan.OMISSION ? sfc2 : sfc1;
        SimpleFeatureCollection reference = Analisis == DeteksiKelengkapan.OMISSION ? sfc1 : sfc2;
        SimpleFeatureType sft = source.getSchema();
        SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder();
        b.setName(Analisis == DeteksiKelengkapan.OMISSION ? "OMISSION" : "COMMISSION");
        b.setCRS(sft.getCoordinateReferenceSystem());
        List<String> theAttributes = (Analisis == DeteksiKelengkapan.OMISSION ? attributes2 : attributes1);
        theAttributes.forEach((key) -> {
            logger.log(Level.INFO, "key {0}", key);
            b.add(sft.getDescriptor(key));
        });
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(b.buildFeatureType());
        final List<SimpleFeature> result = new ArrayList<>();
        DefaultFeatureCollection newCollection = new DefaultFeatureCollection();
        int idd = 1;

        try (SimpleFeatureIterator sourceIterator = source.features()) {
            while (sourceIterator.hasNext()) {
                SimpleFeature sourceFeature = sourceIterator.next();
                Geometry sGeom = (Geometry) sourceFeature.getDefaultGeometry();
                System.out.println("id source " + sourceFeature.getID());
                boolean bertampalan = false;
                try (SimpleFeatureIterator clipIterator = reference.features()) {
                    while (clipIterator.hasNext()) {
                        final SimpleFeature clipFeature = clipIterator.next();
                        Geometry cGeom = (Geometry) clipFeature.getDefaultGeometry();
                        bertampalan = sGeom.intersects(cGeom);
                        if (bertampalan) {
                            bertampalan = true;
                            break;
                        }
                    }
                }
                System.out.println("geometri bertampalan " + bertampalan);
                if (bertampalan == false) {
                    for (String key : theAttributes) {
                        if (key.equals(sft.getGeometryDescriptor().getLocalName())) {
                            featureBuilder.add(sGeom);
                        } else {
                            featureBuilder.add(sourceFeature.getAttribute(key));
                        }
                    }
                    sourceFeature = featureBuilder.buildFeature(String.valueOf(idd));
                    result.add((SimpleFeature) sourceFeature);
                    idd++;
                }
            }
        }
        ListFeatureCollection collection = new ListFeatureCollection(b.buildFeatureType(), result);
        collection.forEach((object) -> {
            newCollection.add(object);
        });
        return newCollection.collection();
    }

    private static boolean isGeometryTypeIn(final Class test, final Class... targets) {
        for (final Class target : targets) {
            if (target.isAssignableFrom(test)) {
                return true;
            }
        }
        return false;
    }

    private static IntersectionFeatureCollection.IntersectionMode getIntersectionMode(DeteksiKelengkapan DeteksiKelengkapan) {
        if (DeteksiKelengkapan == DeteksiKelengkapan.COMMISSION) {
            return IntersectionFeatureCollection.IntersectionMode.FIRST;
        }
        if (DeteksiKelengkapan == DeteksiKelengkapan.OMISSION) {
            return IntersectionFeatureCollection.IntersectionMode.SECOND;
        }

        return null;
    }

    private static FeatureJSON generateFeatureJson(final SimpleFeatureType featureType, final boolean includeCrs) {
        final GeometryJSON geometryJson = new GeometryJSON(GEOMETRY_PRECISION);
        final FeatureJSON featureJson = new FeatureJSON(geometryJson);
        featureJson.setFeatureType(featureType);
        featureJson.setEncodeFeatureBounds(true);
        featureJson.setEncodeFeatureCRS(includeCrs);
        featureJson.setEncodeFeatureCollectionBounds(true);
        featureJson.setEncodeFeatureCollectionCRS(includeCrs);
        return featureJson;
    }

    private SimpleFeatureCollection getFeatureCollection(String typeName) throws IOException {
        return dataStore.getFeatureSource(typeName).getFeatures();
    }

    private String[] getTypeNames() throws IOException {
        return dataStore.getTypeNames();
    }

    private SimpleFeatureType getSchema(String name) throws IOException {
        return dataStore.getSchema(name);
    }

    public DeteksiKelengkapanPY(String capabilities) {
        aquireDataStoreWFS(capabilities);
    }

    public void aquireDataStoreWFS(String capabilities) {
        if (dataStore == null) {
            try {
                Map<String, Serializable> connectionParameters = new HashMap<>();
                connectionParameters.put(WFSDataStoreFactory.URL.key, capabilities);
                dataStore = DataStoreFinder.getDataStore(connectionParameters);
            } catch (IOException e) {
            }
        }
    }
}
