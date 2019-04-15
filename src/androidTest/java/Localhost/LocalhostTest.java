package Localhost;

import android.content.Context;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import io.split.android.client.LocalhostSplitClient;
import io.split.android.client.LocalhostSplitFactory;
import io.split.android.client.LocalhostSplitManager;
import io.split.android.client.SplitResult;
import io.split.android.client.api.SplitView;

public class LocalhostTest {

    @Before
    public void  setUp() {
    }

    @Test
    public void testUsingYamlFile() {

        Context context = InstrumentationRegistry.getTargetContext();

        LocalhostSplitFactory factory = null;
        LocalhostSplitClient client = null;
        LocalhostSplitManager manager = null;
        try {
            factory = new LocalhostSplitFactory("key", context);
            client = (LocalhostSplitClient) factory.client();
            manager = (LocalhostSplitManager) factory.manager();

        } catch (IOException e) {
        }

        List<SplitView> splits = manager.splits();
        SplitView sv0 = manager.split("split_0");
        SplitView sv1 = manager.split("split_1");

        SplitView svx = manager.split("x_feature");

        String s0Treatment = client.getTreatment("split_0", null);
        SplitResult s0Result = client.getTreatmentWithConfig("split_0", null);

        String s1Treatment_hasKey = client.getTreatment("split_1:haskey", null);
        SplitResult s1Result_hasKey = client.getTreatmentWithConfig("split_1:haskey", null);

        String xTreatment = client.getTreatment("x_feature", null);
        SplitResult xResult = client.getTreatmentWithConfig("x_feature", null);

        String xTreatment_key = client.getTreatment("x_feature:key", null);
        SplitResult xResult_key = client.getTreatmentWithConfig("x_feature:key", null);

        String nonExistingTreatment = client.getTreatment("nonExistingTreatment", null);

        Assert.assertNotNull(factory);
        Assert.assertNotNull(client);
        Assert.assertNotNull(manager);

        Assert.assertEquals("off", s0Treatment);
        Assert.assertEquals("off", s0Result.treatment());
        Assert.assertEquals("{ \"size\" : 20 }", s0Result.config());

        Assert.assertEquals("on", s1Treatment_hasKey);
        Assert.assertEquals("on", s1Result_hasKey.treatment());
        Assert.assertNull(s1Result_hasKey.config());

        Assert.assertEquals("on", xTreatment);
        Assert.assertEquals("on", xResult.treatment());
        Assert.assertNull(xResult_key.config());

        Assert.assertEquals("on", xTreatment_key);
        Assert.assertEquals("on", xResult_key.treatment());
        Assert.assertNull(xResult_key.config());

        Assert.assertEquals("control", nonExistingTreatment);

        Assert.assertEquals(9, splits.size());

        Assert.assertNotNull(sv0);
        Assert.assertEquals(sv0.treatments.get(0) , "off");
        Assert.assertEquals(sv0.configs.get("off") , "{ \"size\" : 20 }");

        Assert.assertNotNull(sv1);
        Assert.assertEquals(sv1.treatments.get(0) , "on");
        Assert.assertNull(sv1.configs);

        Assert.assertNotNull(svx);
        Assert.assertEquals(svx.treatments.get(0) , "red");
        Assert.assertEquals(svx.treatments.get(1) , "on");
        Assert.assertEquals(svx.treatments.get(2) , "off");
        Assert.assertNull(svx.configs.get("on"));
        Assert.assertEquals("{\"desc\" : \"this applies only to OFF and only for only_key. The rest will receive ON\"}", svx.configs.get("off"));
        Assert.assertNull(svx.configs.get("red"));

    }

    @Test
    public void testUsingPropertiesFile() {

        Context context = InstrumentationRegistry.getTargetContext();

        LocalhostSplitFactory factory = null;
        LocalhostSplitClient client = null;
        LocalhostSplitManager manager = null;
        try {
            factory = new LocalhostSplitFactory("key", context, "splits_test");
            client = (LocalhostSplitClient) factory.client();
            manager = (LocalhostSplitManager) factory.manager();

        } catch (IOException e) {
        }

        List<SplitView> splits = manager.splits();
        SplitView sva = manager.split("split_a");
        SplitView svb = manager.split("split_b");
        SplitView svd = manager.split("split_d");

        Assert.assertNotNull(factory);
        Assert.assertNotNull(client);
        Assert.assertNotNull(manager);

        String ta = client.getTreatment("split_a", null);
        String tb = client.getTreatment("split_b", null);
        String tc = client.getTreatment("split_c", null);
        String td = client.getTreatment("split_d", null);

        String nonExistingTreatment = client.getTreatment("nonExistingTreatment", null);

        Assert.assertEquals("on", ta);
        Assert.assertEquals("off", tb);
        Assert.assertEquals("red", tc);
        Assert.assertEquals("control", td);

        Assert.assertEquals(3, splits.size());

        Assert.assertNotNull(sva);
        Assert.assertEquals(sva.treatments.get(0) , "on");
        Assert.assertNull(sva.configs);

        Assert.assertNotNull(svb);
        Assert.assertEquals(svb.treatments.get(0) , "off");
        Assert.assertNull(svb.configs);

        Assert.assertNull(svd);
    }

    @Test
    public void testNonExistingFile() {

        Context context = InstrumentationRegistry.getTargetContext();

        LocalhostSplitFactory factory = null;
        LocalhostSplitClient client = null;
        LocalhostSplitManager manager = null;
        try {
            factory = new LocalhostSplitFactory("key", context, "splits_test_not_found");
            client = (LocalhostSplitClient) factory.client();
            manager = (LocalhostSplitManager) factory.manager();

        } catch (IOException e) {
        }

        List<SplitView> splits = manager.splits();
        SplitView sva = manager.split("split_a");

        Assert.assertNotNull(factory);
        Assert.assertNotNull(client);
        Assert.assertNotNull(manager);

        String ta = client.getTreatment("split_a", null);
        String tb = client.getTreatment("split_b", null);
        String tc = client.getTreatment("split_c", null);
        String td = client.getTreatment("split_d", null);

        String nonExistingTreatment = client.getTreatment("nonExistingTreatment", null);

        Assert.assertEquals("control", ta);
        Assert.assertEquals("control", tb);
        Assert.assertEquals("control", tc);
        Assert.assertEquals("control", td);

        Assert.assertEquals(0, splits.size());

        Assert.assertNull(sva);
    }

}
