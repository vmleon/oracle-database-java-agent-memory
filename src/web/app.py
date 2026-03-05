import os
import uuid

import requests
import streamlit as st

st.title("Chat")

# Sidebar config
backend_url = st.sidebar.text_input(
    "Backend URL",
    value=os.getenv("BACKEND_URL", "http://localhost:8080"),
)

# Session state init
if "conversation_id" not in st.session_state:
    st.session_state.conversation_id = str(uuid.uuid4())
if "messages" not in st.session_state:
    st.session_state.messages = []

# Render history
for msg in st.session_state.messages:
    with st.chat_message(msg["role"]):
        st.markdown(msg["content"])

# Handle input
if prompt := st.chat_input("Type a message..."):
    st.session_state.messages.append({"role": "user", "content": prompt})
    with st.chat_message("user"):
        st.markdown(prompt)

    with st.chat_message("assistant"):
        with st.spinner("Thinking..."):
            try:
                resp = requests.post(
                    f"{backend_url.rstrip('/')}/api/v1/agent/chat",
                    data=prompt,
                    headers={
                        "Content-Type": "text/plain",
                        "X-Conversation-Id": st.session_state.conversation_id,
                    },
                    timeout=120,
                )
                resp.raise_for_status()
                answer = resp.text
            except Exception as e:
                answer = f"Error contacting backend: {e}"
        st.markdown(answer)
    st.session_state.messages.append({"role": "assistant", "content": answer})
