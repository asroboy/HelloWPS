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
import static org.geoserver.test.wps.IntersectionFeatureCollection.isGeometryTypeIn;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.vector.IntersectionFeatureCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author asrofiridho
 */
@DescribeProcess(title = "kualitasKelengkapan", description = "Menghitung Kualitas Kelengkapan Omisi atau Komisi")
public class HitungTingkatKelengkapanPolygon implements GeoServerProcess {

    private static final int GEOMETRY_PRECISION = 8;
    DataStore dataStore = null;

    public enum TujuanPerhitungan {
        OMISI,
        KOMISI
    }

    @DescribeResult(name = "result", description = "Output / Hasil")
    public String execute(@DescribeParameter(name = "Data Omisi/Komisi", description = "Data Omisi atau Komisi") SimpleFeatureCollection dataPerhitungan,
            @DescribeParameter(name = "Data Pembanding/Referensi (feature collection)", description = "Data Pembanding (Second feature collection)") SimpleFeatureCollection dataPembanding) throws IOException {

        final Class dataPerhitunganGeomType = ((SimpleFeatureType) dataPerhitungan.getSchema()).getGeometryDescriptor().getType().getBinding();
        final Class dataPembandingGeomType = ((SimpleFeatureType) dataPembanding.getSchema()).getGeometryDescriptor().getType().getBinding();
        if (!isGeometryTypeIn(dataPerhitunganGeomType, MultiPolygon.class, Polygon.class, MultiLineString.class, LineString.class)) {
            throw new IllegalArgumentException("Data Omisi/Komisi Harus Polygon");
        }
        if (!isGeometryTypeIn(dataPembandingGeomType, MultiPolygon.class, Polygon.class, MultiLineString.class, LineString.class)) {
            throw new IllegalArgumentException("Data Pembanding Harus Polygon");
        }

        String result = "-------------- Hasil Perhitungan----------------";

        int rowCountDataOmisiKomisi = dataPerhitungan.size();
        int rowCountDataPembandung = dataPembanding.size();
        
        double prosentase = rowCountDataOmisiKomisi / rowCountDataPembandung * 100;
        result += "\nJumlah row data pembanding/ref :" + rowCountDataPembandung;
        result += "\nJumlah row data Omisi / Komisi :" + rowCountDataOmisiKomisi;
        result += "\n---------------------------------------------";
        result += "Prosentase Data Omisi  :" + String.valueOf(prosentase)+ "%";

        return result;
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

    public HitungTingkatKelengkapanPolygon(String capabilities) {
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
