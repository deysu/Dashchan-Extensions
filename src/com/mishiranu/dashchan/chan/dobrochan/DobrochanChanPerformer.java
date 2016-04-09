package com.mishiranu.dashchan.chan.dobrochan;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class DobrochanChanPerformer extends ChanPerformer
{
	private static final int DELAY = 1000;
	private static final String COOKIE_HANABIRA = "hanabira";
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		DobrochanChanLocator locator = ChanLocator.get(this);
		DobrochanChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.buildPath(data.boardName, data.pageNumber + ".json");
		JSONObject response = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.addCookie(COOKIE_HANABIRA, configuration.getCookie(COOKIE_HANABIRA)).setDelay(DELAY)
				.read().getJsonObject();
		if (response != null)
		{
			try
			{
				JSONObject jsonObject = ((JSONObject) response).getJSONObject("boards").getJSONObject(data.boardName);
				configuration.updateFromThreadsJson(data.boardName, jsonObject);
				JSONArray threadsArray = jsonObject.getJSONArray("threads");
				Posts[] threads = null;
				if (threadsArray != null && threadsArray.length() > 0)
				{
					threads = new Posts[threadsArray.length()];
					for (int i = 0; i < threads.length; i++)
					{
						threads[i] = DobrochanModelMapper.createThread(threadsArray.getJSONObject(i), locator);
					}
				}
				return new ReadThreadsResult(threads);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		DobrochanChanLocator locator = ChanLocator.get(this);
		DobrochanChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri;
		if (data.partialThreadLoading && data.lastPostNumber != null)
		{
			uri = locator.createApiUri("thread", data.boardName, data.threadNumber + "/new.json",
					"last_post", data.lastPostNumber, "new_format", "1", "message_html", "1", "board", "1");
		}
		else
		{
			uri = locator.createApiUri("thread", data.boardName, data.threadNumber + "/all.json",
					"new_format", "1", "message_html", "1", "board", "1");
		}
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.addCookie(COOKIE_HANABIRA, configuration.getCookie(COOKIE_HANABIRA)).setDelay(DELAY)
				.read().getJsonObject();
		handleMobileApiError(jsonObject);
		try
		{
			jsonObject = jsonObject.getJSONObject("result");
			configuration.updateFromPostsJson(data.boardName, jsonObject);
			jsonObject = jsonObject.getJSONArray("threads").getJSONObject(0);
			JSONArray jsonArray = jsonObject.optJSONArray("posts");
			if (jsonArray == null) return null;
			return new ReadPostsResult(DobrochanModelMapper.createPosts(jsonArray, locator, data.threadNumber));
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException, InvalidResponseException
	{
		DobrochanChanLocator locator = ChanLocator.get(this);
		DobrochanChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.createApiUri("post", data.boardName, data.postNumber + ".json",
				"new_format", "1", "message_html", "1", "thread", "1");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).addCookie(COOKIE_HANABIRA,
				configuration.getCookie(COOKIE_HANABIRA)).setDelay(DELAY).read().getJsonObject();
		handleMobileApiError(jsonObject);
		try
		{
			jsonObject = jsonObject.getJSONObject("result").getJSONArray("threads").getJSONObject(0);
			String threadNumber = CommonUtils.getJsonString(jsonObject, "display_id");
			return new ReadSinglePostResult(DobrochanModelMapper.createPost(jsonObject.getJSONArray("posts")
					.getJSONObject(0), locator, threadNumber));
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		DobrochanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("frame.xhtml");
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new DobrochanBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		DobrochanChanLocator locator = ChanLocator.get(this);
		DobrochanChanConfiguration configuration = ChanConfiguration.get(this);
		Uri uri = locator.createApiUri("thread", data.boardName, data.threadNumber + "/last.json",
				"count", "0", "new_format", "1");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.addCookie(COOKIE_HANABIRA, configuration.getCookie(COOKIE_HANABIRA)).setDelay(DELAY)
				.read().getJsonObject();
		handleMobileApiError(jsonObject);
		try
		{
			return new ReadPostsCountResult(jsonObject.getJSONObject("result").getInt("posts_count"));
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	private void handleMobileApiError(JSONObject jsonObject) throws HttpException, InvalidResponseException
	{
		if (jsonObject == null) throw new InvalidResponseException();
		JSONObject errorObject = jsonObject.optJSONObject("error");
		if (errorObject != null)
		{
			String message = CommonUtils.optJsonString(errorObject, "message");
			if (message != null)
			{
				if ("Specified element does not exist.".equals(message)) throw HttpException.createNotFoundException();
				throw new HttpException(0, message);
			}
			throw new InvalidResponseException();
		}
	}
	
	private final HashSet<String> mForceCaptcha = new HashSet<>();
	
	private boolean isForceCaptcha(String boardName, String threadNumber)
	{
		return mForceCaptcha.contains(boardName + "," + threadNumber);
	}
	
	private void setForceCaptcha(String boardName, String threadNumber, boolean forceCaptcha)
	{
		String key = boardName + "," + threadNumber;
		if (forceCaptcha) mForceCaptcha.add(key); else mForceCaptcha.remove(key);
	}
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		DobrochanChanLocator locator = ChanLocator.get(this);
		DobrochanChanConfiguration configuration = ChanConfiguration.get(this);
		String hanabiraCookie = configuration.getCookie(COOKIE_HANABIRA);
		if (!configuration.isAlwaysLoadCaptcha())
		{
			if (!StringUtils.isEmpty(hanabiraCookie))
			{
				if (!isForceCaptcha(data.boardName, data.threadNumber))
				{
					Uri uri = locator.buildPath("api", "user.json");
					JSONObject jsonObject = new HttpRequest(uri, data.holder, data)
							.addCookie(COOKIE_HANABIRA, hanabiraCookie).read().getJsonObject();
					if (jsonObject != null)
					{
						JSONArray jsonArray = jsonObject.optJSONArray("tokens");
						if (jsonArray != null)
						{
							for (int i = 0; i < jsonArray.length(); i++)
							{
								jsonObject = jsonArray.optJSONObject(i);
								if (jsonObject != null)
								{
									String token = CommonUtils.optJsonString(jsonObject, "token");
									if ("no_user_captcha".equals(token))
									{
										CaptchaData captchaData = new CaptchaData();
										captchaData.put(CaptchaData.CHALLENGE, hanabiraCookie);
										return new ReadCaptchaResult(CaptchaState.SKIP, captchaData);
									}
								}
							}
						}
					}
				}
				else setForceCaptcha(data.boardName, data.threadNumber, false);
			}
		}
		Uri uri = locator.buildPath("captcha", data.boardName, System.currentTimeMillis() + ".png");
		Bitmap image = new HttpRequest(uri, data.holder, data).addCookie(COOKIE_HANABIRA, hanabiraCookie)
				.read().getBitmap();
		Bitmap trimmed = CommonUtils.trimBitmap(image, 0xffffffff);
		if (trimmed == null) trimmed = image;
		Bitmap newImage = Bitmap.createBitmap(trimmed.getWidth(), 32, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(newImage);
		int shift = (newImage.getHeight() - trimmed.getHeight()) / 2;
		canvas.drawColor(0xffffffff);
		canvas.drawBitmap(trimmed, 0, shift, null);
		if (trimmed != image) trimmed.recycle();
		image.recycle();
		if (StringUtils.isEmpty(hanabiraCookie))
		{
			hanabiraCookie = data.holder.getCookieValue(COOKIE_HANABIRA);
			configuration.storeCookie(COOKIE_HANABIRA, hanabiraCookie, "Hanabira");
		}
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.CHALLENGE, hanabiraCookie);
		return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(newImage);
	}
	
	public String readThreadId(String boardName, String threadNumber, HttpHolder holder, HttpRequest.Preset preset)
			throws HttpException, InvalidResponseException
	{
		DobrochanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createApiUri("thread", boardName, threadNumber + "/last.json",
				"count", "0", "new_format", "1");
		JSONObject jsonObject = new HttpRequest(uri, holder, preset).setDelay(DELAY).read().getJsonObject();
		handleMobileApiError(jsonObject);
		try
		{
			return CommonUtils.getJsonString(jsonObject.getJSONObject("result"), "thread_id");
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	private static final Pattern PATTERN_POST_ERROR_UNCOMMON = Pattern.compile("<h2>(.*?)</h2>");
	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("<td.*?class='post-error'>(.*?)</td>");
	
	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("thread_id", data.threadNumber);
		entity.add("name", data.name);
		entity.add("subject", data.subject);
		entity.add("message", data.comment);
		entity.add("password", data.password);
		entity.add("goto", "thread");
		if (data.optionSage) entity.add("sage", "on");
		if (data.attachments != null)
		{
			for (int i = 0; i < data.attachments.length; i++)
			{
				SendPostData.Attachment attachment = data.attachments[i];
				attachment.addToEntity(entity, "file_" + (i + 1));
				entity.add("file_" + (i + 1) + "_rating", attachment.rating);
			}
			entity.add("post_files_count", Integer.toString(data.attachments.length));
		}
		String hanabiraCookie = null;
		if (data.captchaData != null)
		{
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
			hanabiraCookie = data.captchaData.get(CaptchaData.CHALLENGE);
		}
		
		DobrochanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "post", "new.xhtml");
		String responseText;
		try
		{
			new HttpRequest(uri, data.holder, data).setPostMethod(entity).addCookie(COOKIE_HANABIRA, hanabiraCookie)
					.setRedirectHandler(HttpRequest.RedirectHandler.NONE).execute();
			if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP)
			{
				uri = data.holder.getRedirectedUri();
				String path = uri.getPath();
				if (path == null) throw new InvalidResponseException();
				if (!path.startsWith("/error"))
				{
					String threadNumber = locator.getThreadNumber(uri);
					if (threadNumber == null) throw new InvalidResponseException();
					return new SendPostResult(threadNumber, null);
				}
				responseText = new HttpRequest(uri, data.holder, data).addCookie(COOKIE_HANABIRA, hanabiraCookie)
						.read().getString();
			}
			else responseText = data.holder.read().getString();
		}
		finally
		{
			data.holder.disconnect();
		}
		
		String errorMessage = null;
		Matcher matcher = PATTERN_POST_ERROR.matcher(responseText);
		if (matcher.find()) errorMessage = matcher.group(1); else
		{
			matcher = PATTERN_POST_ERROR_UNCOMMON.matcher(responseText);
			if (matcher.find()) errorMessage = matcher.group(1);
		}
		if (errorMessage != null)
		{
			int errorType = 0;
			if (errorMessage.contains("Неверная капча") || errorMessage.contains("Нужно включить кукисы")
					|| errorMessage.contains("подтвердите, что вы человек"))
			{
				setForceCaptcha(data.boardName, data.threadNumber, true);
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			}
			else if (errorMessage.contains("Вы должны указать тему или написать сообщение")
					|| errorMessage.contains("Вы должны написать текст"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			}
			else if (errorMessage.contains("Вы должны прикрепить"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			}
			else if (errorMessage.contains("Сообщение не должно превышать"))
			{
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			}
			else if (errorMessage.contains("не существует"))
			{
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			}
			else if (errorMessage.contains("закрыт"))
			{
				errorType = ApiException.SEND_ERROR_CLOSED;
			}
			if (errorType != 0) throw new ApiException(errorType); else
			{
				CommonUtils.writeLog("Dobrochan send message", errorMessage);
				throw new ApiException(errorMessage);
			}
		}
		throw new InvalidResponseException();
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		String threadId = readThreadId(data.boardName, data.threadNumber, data.holder, data);
		DobrochanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "delete");
		UrlEncodedEntity entity = new UrlEncodedEntity("task", "delete", "password", data.password);
		for (String postNumber : data.postNumbers) entity.add(postNumber, threadId);
		String responseText;
		try
		{
			new HttpRequest(uri, data.holder, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.NONE).execute();
			if (data.holder.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) return null;
			responseText = data.holder.read().getString();
		}
		finally
		{
			data.holder.disconnect();
		}
		Matcher matcher = PATTERN_POST_ERROR_UNCOMMON.matcher(responseText);
		if (matcher.find())
		{
			String errorMessage = matcher.group(1);
			if (errorMessage != null)
			{
				int errorType = 0;
				if (errorMessage.contains("Неправильный пароль"))
				{
					errorType = ApiException.DELETE_ERROR_PASSWORD;
				}
				if (errorType != 0) throw new ApiException(errorType);
			}
			CommonUtils.writeLog("Dobrochan delete message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}
}