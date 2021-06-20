package moe.seq.ads.mobile;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.util.HashMap;
import java.util.Map;

class EncodedByteArrayFileRequest extends Request<byte[]> {
    private final Response.Listener<byte[]> mListener;
    private Map<String, String> mParams;

    public Map<String, String> responseHeaders;

    public EncodedByteArrayFileRequest(int method, String mUrl,
                                       Response.Listener<byte[]> listener,
                                       Response.ErrorListener errorListener,
                                       HashMap<String, String> params)
    {
        // TODO Auto-generated constructor stub

        super(method, mUrl, errorListener);
        // this request would never use cache.
        setShouldCache(false);
        mListener = listener;
        mParams = params;
    }

    // NOTE: original answer had this method set as protected
    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return mParams;
    }

    @Override
    protected void deliverResponse(byte[] response) {
        mListener.onResponse(response);
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {

        responseHeaders = response.headers;

        return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
    }
}