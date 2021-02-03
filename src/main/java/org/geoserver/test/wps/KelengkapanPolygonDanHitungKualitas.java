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
import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.vector.IntersectionFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;

/**
 *
 * @author asrofiridho
 */
@DescribeProcess(title = "kelengkapanPolygonDanHitungKualitas", description = "Menghitung Kelengkapan Data (Omisi atau Komisi) dan Kualitas Kelengkapan Omisi atau Komisi. output berupa string informasi")
public final class KelengkapanPolygonDanHitungKualitas implements GeoServerProcess {

    private static final int GEOMETRY_PRECISION = 8;
    DataStore dataStore = null;

    public enum TujuanPerhitungan {
        OMISI,
        KOMISI
    }

    @DescribeResult(name = "result", description = "Output / Hasil")
    public String execute(@DescribeParameter(name = "Data Perhitungan/Uji (feature collection)", description = "Data Perhitungan (First feature collection)") SimpleFeatureCollection dataPerhitungan,
            @DescribeParameter(name = "Data Pembanding/Referensi (feature collection)", description = "Data Pembanding (Second feature collection)") SimpleFeatureCollection dataPembanding,
            @DescribeParameter(name = "Jenis Analisis", description = "Jenis Analisi/Perhitungan yang ingin dilakukan") TujuanPerhitungan jenisAnalisis) throws IOException, Exception {

        final Class dataPerhitunganGeomType = ((SimpleFeatureType) dataPerhitungan.getSchema()).getGeometryDescriptor().getType().getBinding();
        final Class dataPembandingGeomType = ((SimpleFeatureType) dataPembanding.getSchema()).getGeometryDescriptor().getType().getBinding();
        if (!isGeometryTypeIn(dataPerhitunganGeomType, MultiPolygon.class, Polygon.class, MultiLineString.class, LineString.class)) {
            throw new IllegalArgumentException("Data Perhitungan Harus Polygon");
        }
        if (!isGeometryTypeIn(dataPembandingGeomType, MultiPolygon.class, Polygon.class, MultiLineString.class, LineString.class)) {
            throw new IllegalArgumentException("Data Pembanding Harus Polygon");
        }
        List<String> attributes1 = new ArrayList<>();
        dataPerhitungan.getSchema().getAttributeDescriptors().forEach((at) -> {
            attributes1.add(at.getLocalName());
        });
        List<String> attributes2 = new ArrayList<>();
        dataPembanding.getSchema().getAttributeDescriptors().forEach((at) -> {
            attributes2.add(at.getLocalName());
        });
        IntersectionFeatureCollection ifc = new IntersectionFeatureCollection();

//        SimpleFeatureCollection output = ifc.execute(dataPerhitungan, dataPembanding, attributes1, attributes2, getIntersectionMode(jenisAnalisis), Boolean.FALSE, Boolean.TRUE);
        
         SimpleFeatureCollection output = difference(dataPerhitungan, dataPembanding, jenisAnalisis);
        String result = "-------------- Hasil Perhitungan----------------";

        int rowCountDataOmisiKomisi = output.size();
        int rowCountDataPembandung = dataPembanding.size();
        
        double prosentase = rowCountDataOmisiKomisi / rowCountDataPembandung * 100;
        result += "\nJumlah row data pembanding/ref :" + rowCountDataPembandung;
        result += "\nJumlah row data Omisi / Komisi :" + rowCountDataOmisiKomisi;
        result += "\n---------------------------------------------";
        result += "Prosentase Data Omisi  :" + String.valueOf(prosentase) + "%";

        return result;
    }

    
      public SimpleFeatureCollection difference(SimpleFeatureCollection sfc1, SimpleFeatureCollection sfc2, TujuanPerhitungan jenisAnalisis) throws Exception {
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

        SimpleFeatureCollection source = jenisAnalisis == TujuanPerhitungan.OMISI ? sfc1 : sfc2;
        SimpleFeatureCollection reference = jenisAnalisis == TujuanPerhitungan.OMISI ? sfc2 : sfc1;

   
        SimpleFeatureType sft = source.getSchema();

        SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder();
        b.setName(jenisAnalisis == TujuanPerhitungan.OMISI ? "OMISI" : "KOMISI");
        b.setCRS(sft.getCoordinateReferenceSystem());
        b.addAll(sft.getAttributeDescriptors());

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(b.buildFeatureType());
        final List<SimpleFeature> result = new ArrayList<SimpleFeature>();
        DefaultFeatureCollection newCollection = new DefaultFeatureCollection();
        int idd = 1;
        try (SimpleFeatureIterator sourceIterator = source.features()) {
            while (sourceIterator.hasNext()) {
                SimpleFeature sourceFeature = sourceIterator.next();
//                String uuid = (String) sourceFeature.getAttribute("Uuid");
//                String guid = (String) sourceFeature.getAttribute("Guid");
                Geometry sGeom = (Geometry) sourceFeature.getDefaultGeometry();
                for (int i = 0; i < sGeom.getNumGeometries(); i++) {
                    Polygon[] arrPolygon = new Polygon[sGeom.getNumGeometries()];
                    for (int l = 0; l < sGeom.getNumGeometries(); l++) {
                        arrPolygon[l] = (Polygon) sGeom.getGeometryN(i);
                    }
                    MultiPolygon sPolygon = geometryFactory.createMultiPolygon(arrPolygon);
                    SimpleFeatureIterator clipIterator = reference.features();
                    while (clipIterator.hasNext()) {
                        final SimpleFeature clipFeature = clipIterator.next();
                        Geometry cGeom = (Geometry) clipFeature.getDefaultGeometry();
                        for (int j = 0; j < cGeom.getNumGeometries(); j++) {

                            arrPolygon = new Polygon[cGeom.getNumGeometries()];
                            for (int l = 0; l < cGeom.getNumGeometries(); l++) {
                                arrPolygon[l] = (Polygon) cGeom.getGeometryN(j);
                            }
                            MultiPolygon cPolygon = geometryFactory.createMultiPolygon(arrPolygon);
                            //if (sPolygon.disjoint(cPolygon)) break;
                            try {
                                if (sPolygon.intersects(cPolygon)) {
                                    Geometry geometries = sPolygon.difference(cPolygon);
                                    if (geometries.getNumGeometries() == 1) {
                                        arrPolygon = new Polygon[geometries.getNumGeometries()];
                                        if (geometries.getNumGeometries() > 0) {
                                            arrPolygon = new Polygon[geometries.getNumGeometries()];
                                            for (int l = 0; l < geometries.getNumGeometries(); l++) {
                                                arrPolygon[l] = (Polygon) geometries.getGeometryN(l);
                                            }
                                        }
                                        sPolygon = geometryFactory.createMultiPolygon(arrPolygon);
                                    } else {
                                        sPolygon = (MultiPolygon) geometries;
                                    }
                                }
                            } catch (Exception problem) {
                                System.out.println();
                                System.out.println("Notice: 1 difference failed ");
                                System.out.println(problem);
                            }
                        }
                    }
                    clipIterator.close();

                    if (sPolygon.getNumGeometries() > 0) {
                        if (sPolygon.getNumPoints() > 0) {
                            featureBuilder.add(sPolygon);
//                            featureBuilder.add(uuid);
//                            featureBuilder.add(guid);
                            for (AttributeDescriptor attributeDescriptor : sft.getAttributeDescriptors()) {
                                if (!attributeDescriptor.getLocalName().equals("geom")) {
                                    featureBuilder.add(sourceFeature.getAttribute(attributeDescriptor.getLocalName()));
                                }
                            }

                            sourceFeature = featureBuilder.buildFeature(String.valueOf(idd));
                            result.add((SimpleFeature) sourceFeature);
                        }
                    }
                }
                idd++;
            }
        }
        ListFeatureCollection collection = new ListFeatureCollection(b.buildFeatureType(), result);
        collection.forEach((object) -> {
            newCollection.add(object);
        });
        return newCollection.collection();
    }
    
    private boolean isGeometryTypeIn(final Class test, final Class... targets) {
        for (final Class target : targets) {
            if (target.isAssignableFrom(test)) {
                return true;
            }
        }
        return false;
    }

    private static IntersectionFeatureCollection.IntersectionMode getIntersectionMode(TujuanPerhitungan tujuanPerhitungan) {
        if (tujuanPerhitungan == TujuanPerhitungan.KOMISI) {
            return IntersectionFeatureCollection.IntersectionMode.FIRST;
        }
        if (tujuanPerhitungan == TujuanPerhitungan.OMISI) {
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

    public KelengkapanPolygonDanHitungKualitas(String capabilities) {
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
