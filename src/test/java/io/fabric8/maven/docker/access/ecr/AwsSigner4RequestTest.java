package io.fabric8.maven.docker.access.ecr;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;

import io.fabric8.maven.docker.access.AuthConfig;

/**
 * Test aws request signing
 *
 * @author chas
 * @since 2016-12-21
 */
public class AwsSigner4RequestTest {

    private static final String TASK1 = "POST\n"
            + "/\n"
            + "\n"
            + "content-type:application/x-amz-json-1.1\n"
            + "host:ecr.us-east-1.amazonaws.com\n"
            + "x-amz-target:AmazonEC2ContainerRegistry_V20150921.GetAuthorizationToken\n"
            + "\n"
            + "content-type;host;x-amz-target\n"
            + "1531fbe1f1c4e3437223a1583a6d21d404acf9d910262f629d73d6bf55a545bd";

    private static final String TASK2 = "AWS4-HMAC-SHA256\n"
            + "20150830T123600Z\n"
            + "20150830/us-east-1/service/aws4_request\n"
            + "7c945a283983ca83301de46103427b7035344a4b0cfddd886e3d94b4bed7df5e";

    private static final String TASK3 = "89cd649587898a1913ced5c519425905b192c4662212d37e689e6c20e53edbbd";

    private static final String TASK4 = "AWS4-HMAC-SHA256 "
            + "Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
            + "SignedHeaders=content-type;host;x-amz-target, "
            + "Signature=89cd649587898a1913ced5c519425905b192c4662212d37e689e6c20e53edbbd";

    @Test
    public void testSign() throws Exception {
        HttpPost request = new HttpPost("https://ecr.us-east-1.amazonaws.com/");
        request.setHeader("host", "ecr.us-east-1.amazonaws.com");
        request.setHeader("Content-Type", "application/x-amz-json-1.1");
        request.setHeader("X-Amz-Target", "AmazonEC2ContainerRegistry_V20150921.GetAuthorizationToken");
        request.setEntity(new StringEntity("{\"registryIds\":[\"012345678901\"]}", StandardCharsets.UTF_8));

        AwsSigner4 signer = new AwsSigner4("us-east-1", "ecr");

        Date signingTime = AwsSigner4Request.TIME_FORMAT.parse("20150830T123600Z");
        AwsSigner4Request sr = new AwsSigner4Request("us-east-1", "service", request, signingTime);
        AuthConfig credentials = new AuthConfig("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null, null);
 
        Assert.assertEquals(TASK1, signer.task1(sr));

        Assert.assertEquals(TASK2, signer.task2(sr));

        StringBuilder dst = new StringBuilder();
        AwsSigner4.hexEncode(dst, signer.task3(sr, credentials));
        Assert.assertEquals(TASK3, dst.toString());

        Assert.assertEquals(TASK4, signer.task4(sr, credentials));
    }

}
