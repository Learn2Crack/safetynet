package com.learn2crack.safetynet;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.gson.Gson;
import com.learn2crack.safetynet.model.JWS;
import com.learn2crack.safetynet.model.JWSRequest;
import com.learn2crack.safetynet.model.Response;
import com.learn2crack.safetynet.network.RetrofitInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks {

    public static final String GOOGLE_API_VERIFY_URL = "https://www.googleapis.com/androidcheck/v1/attestations/";


    public static final String TAG = MainActivity.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    private boolean isConnected = false;

    private FloatingActionButton mButton;
    private CardView mCvIntegrity;
    private CardView mCvCTS;
    private ImageView mIvIntegrity;
    private ImageView mIvCTS;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initClient();
    }

    private void initViews() {

        mButton = (FloatingActionButton) findViewById(R.id.btn_proceed);
        mCvIntegrity = (CardView) findViewById(R.id.card_integrity);
        mCvCTS = (CardView) findViewById(R.id.card_cts);
        mIvIntegrity = (ImageView) findViewById(R.id.img_integrity);
        mIvCTS = (ImageView) findViewById(R.id.img_cts);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (isConnected) {

                    mCvCTS.setVisibility(View.INVISIBLE);
                    mCvIntegrity.setVisibility(View.INVISIBLE);
                    mProgressBar.setVisibility(View.VISIBLE);
                    startVerification();

                } else {

                    Toast.makeText(MainActivity.this, "Client not connected !", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initClient() {

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(SafetyNet.API)
                .addConnectionCallbacks(this)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        Log.d(TAG, "onConnected: ");

        isConnected = true;

    }

    @Override
    public void onConnectionSuspended(int i) {

        isConnected = false;
    }

    private void startVerification() {

        final byte[] nonce = getRequestNonce();

        SafetyNet.SafetyNetApi.attest(mGoogleApiClient, nonce)
                .setResultCallback(new ResultCallback<SafetyNetApi.AttestationResult>() {
                    @Override
                    public void onResult(@NonNull SafetyNetApi.AttestationResult attestationResult) {

                        Status status = attestationResult.getStatus();

                        if (status.isSuccess()) {

                            String  jwsResult = attestationResult.getJwsResult();

                            verifyOnline(jwsResult);

                        } else {

                            mProgressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Error !", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void verifyOnline(final String jws) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(GOOGLE_API_VERIFY_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitInterface retrofitInterface = retrofit.create(RetrofitInterface.class);

        JWSRequest jwsRequest = new JWSRequest();
        jwsRequest.setSignedAttestation(jws);
        Call<Response> responseCall = retrofitInterface.getResult(jwsRequest, getString(R.string.api_key));

        responseCall.enqueue(new Callback<Response>() {
            @Override
            public void onResponse(Call<Response> call, retrofit2.Response<Response> response) {

                boolean result = response.body().isValidSignature();

                if (result) {

                    decodeJWS(jws);

                } else {

                    mProgressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Verification Error !", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Response> call, Throwable t) {

                mProgressBar.setVisibility(View.GONE);
                Log.d(TAG, "onFailure: "+t.getLocalizedMessage());
                Toast.makeText(MainActivity.this, t.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void decodeJWS(String jwsString) {

        byte[] json = Base64.decode(jwsString.split("[.]")[1],Base64.DEFAULT);
        String text = new String(json, StandardCharsets.UTF_8);

        Gson gson = new Gson();
        JWS jws = gson.fromJson(text, JWS.class);

        displayResults(jws.isBasicIntegrity(), jws.isCtsProfileMatch());
    }

    private byte[] getRequestNonce() {

        String data = String.valueOf(System.currentTimeMillis());

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byte[] bytes = new byte[24];
        Random random = new Random();
        random.nextBytes(bytes);
        try {
            byteStream.write(bytes);
            byteStream.write(data.getBytes());
        } catch (IOException e) {
            return null;
        }

        return byteStream.toByteArray();
    }

    private void displayResults(boolean integrity, boolean cts) {

        mProgressBar.setVisibility(View.GONE);

        if (integrity) {

            mIvIntegrity.setImageResource(R.drawable.ic_check_circle_white);
            mCvIntegrity.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorSuccess));

        } else {

            mIvIntegrity.setImageResource(R.drawable.ic_error_white);
            mCvIntegrity.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorFailure));
        }

        mCvIntegrity.setVisibility(View.VISIBLE);

        if (cts) {

            mIvCTS.setImageResource(R.drawable.ic_check_circle_white);
            mCvCTS.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorSuccess));

        } else {

            mIvCTS.setImageResource(R.drawable.ic_error_white);
            mCvCTS.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorFailure));
        }

        mCvCTS.setVisibility(View.VISIBLE);
    }

}
