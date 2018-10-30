package com.hr.photoparty;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.android.view.CardInputWidget;

import org.json.JSONException;
import org.json.JSONObject;

import static com.hr.photoparty.Util.MAX_FREE_UPLOAD_COUNT;
import static com.hr.photoparty.Util.PAYMENT_MADE;

/**
 * Created by RabbitJang on 10/25/2018.
 */

public class SettingActivity extends AppCompatActivity {
    Dialog stripeDialog;
    TextView uploadedCntTv;
    TextView remainingCntTv;
    TextView purchaseTv;
    Button purchaseBt;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        configureDesign();
    }

    private void configureDesign() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.colorAccent)));
        getSupportActionBar().setTitle(Html.fromHtml("<small>Setting</small>"));

        TextView nameTv = findViewById(R.id.textView6);
        nameTv.setText(SharedData.getInstance().userName);
        uploadedCntTv = findViewById(R.id.textView4);
        remainingCntTv = findViewById(R.id.textView5);
        purchaseTv = findViewById(R.id.textView2);
        purchaseBt = findViewById(R.id.button);

        updatePage();

        purchaseBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStripeDialog();
            }
        });
    }

    private void updatePage() {
        if(SharedData.getInstance().uploadedCount == 1) {
            uploadedCntTv.setText(String.format("%d photo", SharedData.getInstance().uploadedCount));
        }
        else {
            uploadedCntTv.setText(String.format("%d photos", SharedData.getInstance().uploadedCount));
        }

        if(SharedData.getInstance().paid == 1) {
            remainingCntTv.setText("Unlimited Upload");
            purchaseBt.setVisibility(View.INVISIBLE);
            purchaseTv.setVisibility(View.INVISIBLE);
        }
        else {
            remainingCntTv.setText(String.format("%d photos remaining", MAX_FREE_UPLOAD_COUNT - SharedData.getInstance().uploadedCount));
        }
    }

    private void showStripeDialog() {
        stripeDialog = new Dialog(SettingActivity.this);
        stripeDialog.requestWindowFeature(getWindow().FEATURE_NO_TITLE);
        stripeDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        stripeDialog.setContentView(R.layout.dialog_stripe);
        stripeDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        stripeDialog.getWindow().setGravity(Gravity.CENTER);

        final CardInputWidget mCardInputWidget = (CardInputWidget) stripeDialog.findViewById(R.id.card_input_widget);

        Button closeBt = stripeDialog.findViewById(R.id.button20);
        closeBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stripeDialog.dismiss();
            }
        });
        Button bookBt = (Button) stripeDialog.findViewById(R.id.button12);
        bookBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Card card = mCardInputWidget.getCard();
                if (card == null) {
                    Util.showToast("Please input valid card information", SettingActivity.this);
                }
                else {
                    Util.showProgressDialog("Updating..", SettingActivity.this);
                    getStripeTokenWithCard(card);
                }
            }
        });
        stripeDialog.show();
    }

    private void getStripeTokenWithCard(Card card) {
        Stripe stripe = new Stripe(this, SharedData.getInstance().publicKey);
        stripe.createToken(card, new TokenCallback() {
                    public void onSuccess(Token token) {
                        createCustomer(token.getId());
                    }
                    public void onError(Exception error) {
                        Util.hideProgressDialog();
                        Util.showToast(error.getLocalizedMessage(), SettingActivity.this);
                    }
                }
        );
    }

    private void createCustomer(String cardToken) {
        APIManager.getInstance().setCallback(new APIManagerCallback() {
            @Override
            public void APICallback(JSONObject objAPIResult) {
                Util.hideProgressDialog();
                if(objAPIResult == null) {
                    Util.showToast("Create customer failed and try again", SettingActivity.this);
                    return;
                }
                try {
                    SharedData.getInstance().customerId = objAPIResult.getString("CustomerId");
                    chargeMoney();
                } catch (JSONException e) {
                    Util.hideProgressDialog();
                    e.printStackTrace();
                }
            }
        });

        JSONObject object = new JSONObject();
        String userEmail = String.format("%s@photoparty.com", SharedData.getInstance().userName.replace(" ", ""));

        try {
            object.accumulate("CustomerId", "");
            object.accumulate("Email", userEmail);
            object.accumulate("CardToken", cardToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        APIManager.getInstance().createCustomer(object);
    }

    private void chargeMoney() {
        APIManager.getInstance().setCallback(new APIManagerCallback() {
            @Override
            public void APICallback(JSONObject objAPIResult) {
                Util.hideProgressDialog();
                if(objAPIResult == null) {
                    Util.showToast("Charge failed and try again", SettingActivity.this);
                    return;
                }
                try {
                    String message = objAPIResult.getString("Message");
                    if(objAPIResult.getBoolean("Success")) {
                        SharedData.getInstance().paid = 1;
                        Util.showToast(message, SettingActivity.this);
                        stripeDialog.hide();
                        updatePage();
                        MainActivity.handler.sendEmptyMessage(PAYMENT_MADE);
                    }
                    else {
                        Util.showToast(message, SettingActivity.this);
                    }
                } catch (JSONException e) {
                    Util.hideProgressDialog();
                    e.printStackTrace();
                }
            }
        });

        JSONObject object = new JSONObject();
        try {
            object.accumulate("Description", String.format("Payment from Photo Party user %s", SharedData.getInstance().userName));
            object.accumulate("CustomerId", SharedData.getInstance().customerId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Util.showProgressDialog("Charging..", SettingActivity.this);
        APIManager.getInstance().charge(object);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
