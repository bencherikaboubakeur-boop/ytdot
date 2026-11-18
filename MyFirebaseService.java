String url = msg.getData().get("url");
if (url != null && !url.startsWith("https://")) url = null;
